package ot.geckopipe.index

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel
import ot.geckopipe.Configuration
import ot.geckopipe.positional.VEP

/** represents a cached table of variants with all variant columns
  *
  * columns as chr_id, position, ref_allele, alt_allele, variant_id, rs_id. Also
  * this table is persisted and sorted by (chr_id, position) by default
  */
abstract class VariantIndex extends Indexable {
  val columns: Seq[String] = Seq("chr_id", "position", "ref_allele", "alt_allele", "variant_id", "rs_id")
  val indexColumns: Seq[String] = Seq("chr_id", "position")

  lazy val aggByVariant: DataFrame = aggBy(indexColumns, columns)
}

/** The companion object helps to build VariantIndex from Configuration and SparkSession */
object VariantIndex {
  /** variant_id is represented as 1_123_T_C but splitted into columns 1 23456 T C */
  val variantColumnNames: List[String] = List("chr_id", "position", "ref_allele", "alt_allele")

  /** types of the columns named in variantColumnNames */
  val variantColumnTypes: List[String] = List("String", "long", "string", "string")

  /** this class build based on the Configuration it creates a VariantIndex */
  class Builder (val conf: Configuration, val ss: SparkSession) extends LazyLogging {
    def load: VariantIndex = {
      logger.info("loading variant index as specified in the configuration")
      // load from configuration
      val vIdx = ss.read
        .format("parquet")
        .load(conf.variantIndex.path)
        .persist(StorageLevel.DISK_ONLY)

      new VariantIndex {
        override def table: DataFrame = vIdx
      }
    }

    def build: VariantIndex = {
      logger.info("building variant index as specified in the configuration")
      val savePath = conf.variantIndex.path.stripSuffix("*")

      val vep = VEP.loadHumanVEP(conf.vep.homoSapiensCons)(ss)
        .drop("qual", "filter", "info")
        .select("chr_id", "position", "ref_allele", "alt_allele", "variant_id", "rs_id")
        .repartitionByRange(col("chr_id").asc, col("position").asc)
        .persist(StorageLevel.DISK_ONLY)

      vep.write.parquet(savePath)

      new VariantIndex {
        override def table: DataFrame = vep
      }
    }
  }

  /** builder object to load or build the VariantIndex */
  def builder(conf: Configuration)(implicit ss: SparkSession): Builder = new Builder(conf, ss)
}
