package ot.geckopipe

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, SparkSession}
import ot.geckopipe.functions._
import ot.geckopipe.index.V2GIndex.Component
import ot.geckopipe.index.VariantIndex

object QTL extends LazyLogging {
  val features: Seq[String] = Seq("qtl_beta", "qtl_se", "qtl_pval", "qtl_score", "qtl_score_q")

  def load(from: String)(implicit ss: SparkSession): DataFrame = {
    val qtl = ss.read
      .parquet(from)
      .withColumn("filename", input_file_name)
      .withColumnRenamed("bio_feature", "feature")
      .withColumnRenamed("chrom", "chr_id")
      .withColumnRenamed("pos", "position")
      .withColumnRenamed("ref", "ref_allele")
      .withColumnRenamed("alt", "alt_allele")
      .withColumnRenamed("beta", "qtl_beta")
      .withColumnRenamed("se", "qtl_se")
      .withColumnRenamed("pval", "qtl_pval")

    qtl
  }

  /** union all intervals and interpolate variants from intervals */
  def apply(vIdx: VariantIndex, conf: Configuration)(implicit ss: SparkSession): Component = {
    val extractValidTokensFromPathUDF = udf((path: String) => extractValidTokensFromPath(path, "/qtl/"))

    logger.info("generate pchic dataset from file and aggregating by range and gene")
    val qtls = load(conf.qtl.path)
      .withColumn("tokens", extractValidTokensFromPathUDF(col("filename")))
      .withColumn("type_id", lower(col("tokens").getItem(0)))
      .withColumn("source_id", lower(col("tokens").getItem(1)))
      .withColumn("qtl_score", -log(10, col("qtl_pval")))
      .drop("filename", "tokens")
      .repartitionByRange(col("chr_id").asc, col("position").asc)
      .sortWithinPartitions(col("chr_id").asc, col("position").asc)

    val vIdxS = vIdx.table.select(VariantIndex.columns.head, VariantIndex.columns.tail: _*)
    val qtlTable = qtls.join(vIdxS, VariantIndex.columns)

    qtlTable.persist

    val qtlWP =
      computeScore(qtlTable, "qtl_score", "qtl_score_q")

    new Component {
      /** unique column name list per component */
      override val features: Seq[String] = QTL.features
      override val table: DataFrame = qtlWP
    }
  }
}
