package ot.geckopipe.index

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import ot.geckopipe.functions._
import ot.geckopipe.{Chromosomes, Configuration}

/** represents a cached table of variants with all variant columns
  *
  * columns as chr_id, position, ref_allele, alt_allele, variant_id, rs_id. Also
  * this table is persisted and sorted by (chr_id, position) by default
  */
abstract class V2GIndex extends Indexable {
  /** compute stats with this resulted table but only when info enabled */
  def computeStats(implicit ss: SparkSession): Seq[Long] = {
    import ss.implicits._
    val totalRows = table.count()
    // val rsidNullsCount = dataset.where($"rsid".isNull).count()
    val inChrCount = table.where($"chr_id".isin(Chromosomes.chrList:_*)).count()

    logger.info(s"count number of rows in chr range $inChrCount of a total $totalRows")
    Seq(inChrCount, totalRows)
  }
}

object V2GIndex extends LazyLogging  {
  trait Component {
    /** unique column name list per component */
    val features: Seq[String]
    val table: DataFrame
  }

  /** all data sources to incorporate needs to meet this format at the end
    *
    * One example of the shape of the data could be
    * "1_123_T_C ENSG0000001 gtex uberon_0001 1
    */
  val features: Seq[String] = Seq("feature", "type_id", "source_id")

  /** columns to index the dataset */
  val indexColumns: Seq[String] = Seq("chr_id", "position")
  /** the whole list of columns this dataset will be outputing */
  val columns: Seq[String] = (VariantIndex.columns ++ EnsemblIndex.columns ++ features).distinct

  /** set few columns to NaN and []
    *
    * TODO this needs a bit of refactoring to do it properly
    */
  def fillAndCompute(ds: DataFrame): DataFrame = {
    // fill missing values
    val filledDS = ds
      .na.fill(Map(
      "interval_score" -> Double.NaN,
      "qtl_beta" -> Double.NaN,
      "qtl_se" -> Double.NaN,
      "qtl_pval" -> Double.NaN,
      "fpred_scores" -> Seq.empty
    ))

    // stringify array columns
    filledDS
      .withColumn("fpred_labels", stringifyColumnString(col("fpred_labels")))
      .withColumn("fpred_scores", stringifyColumnDouble(col("fpred_scores")))
  }

  /** join built gtex and vep together and generate char pos alleles columns from variant_id */
  def build(datasets: Seq[Component], vIdx: VariantIndex, conf: Configuration)
           (implicit ss: SparkSession): V2GIndex = {

    logger.info("build variant to gene dataset union the list of datasets")
    logger.info("load ensembl gene to transcript table, aggregate by gene_id and cache to enrich results")
    val geneTrans = EnsemblIndex(conf.ensembl.geneTranscriptPairs)
      .aggByGene
      .cache

    val allFeatures = datasets.foldLeft(datasets.head.features)((agg, el) => agg ++ el.features).distinct

    val processedDts = datasets.map( el => {
      val table = (allFeatures diff el.features).foldLeft(el.table)((agg, el) => agg.withColumn(el, lit(null)))
      table.join(geneTrans, Seq("gene_id"))
    })

    // TODO remove all null by default values as NaN and []
    val allDts = concatDatasets(processedDts, (columns ++ allFeatures).distinct)
    val postDts = fillAndCompute(allDts)

    new V2GIndex {
      override val table: DataFrame = postDts
    }
  }

  /** join built gtex and vep together and generate char pos alleles columns from variant_id */
  def load(conf: Configuration)(implicit ss: SparkSession): V2GIndex = {

    logger.info("load variant to gene dataset from built one")
    val v2g = ss.read
      .format("csv")
      .option("header", "true")
      .option("delimiter","\t")
      .load(conf.variantGene.path)

    new V2GIndex {
      /** uniform way to get the dataframe */
      override val table: DataFrame = v2g
    }
  }
}
