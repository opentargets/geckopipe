package ot.geckopipe.index

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel
import ot.geckopipe.Configuration
import ot.geckopipe.positional.VEP
import ot.geckopipe.functions._

/** represents a cached table of variants with all variant columns
  *
  * columns as chr_id, position, ref_allele, alt_allele, variant_id, rs_id. Also
  * this table is persisted and sorted by (chr_id, segment, position) by default
  * @param df the DataFrame
  */
class VariantIndex private(val df: DataFrame) {
  def table: DataFrame = df
}

/** The companion object helps to build VariantIndex from Configuration and SparkSession */
object VariantIndex {
  /** this class build based on the Configuration it creates a VariantIndex */
  class Builder (val conf: Configuration, val ss: SparkSession) extends LazyLogging {
    def load: VariantIndex = {
      logger.info("loading variant index as specified in the configuration")
      // load from configuration
      val vIdx = ss.read
        .format("parquet")
        .load(conf.variantIndex.path)
        .persist(StorageLevel.DISK_ONLY)

      new VariantIndex(vIdx)
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

      new VariantIndex(vep)
    }
  }

  /** builder object to load or build the VariantIndex */
  def builder(conf: Configuration)(implicit ss: SparkSession): Builder = new Builder(conf, ss)
}
