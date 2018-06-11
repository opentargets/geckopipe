package ot.geckopipe.index

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}

import ot.geckopipe.functions._

/** This class represents a full table of gene with its transcripts grch37 */
class EnsemblIndex private(val df: DataFrame) {
  /** get the raw DataFrame */
  def table: DataFrame = df
  /** get a DataFrame aggregated by gene_chr and gene_id */
  def aggByGene: DataFrame = df
    .select("gene_chr", "gene_id", "gene_start", "gene_end", "gene_name", "gene_segment")
    .groupBy("gene_chr", "gene_id")
      .agg(first(col("gene_start")).as("gene_start"),
        first(col("gene_end")).as("gene_end"),
        first(col("gene_name")).as("gene_name"),
        first(col("gene_segment")).as("gene_segment")
      )
}

/** Companion object to build the EnsemblIndex class */
object EnsemblIndex {
  val schema = StructType(
    StructField("Gene stable ID", StringType) ::
      StructField("Transcript stable ID", StringType) ::
      StructField("Gene start (bp)", LongType) ::
      StructField("Gene end (bp)", LongType) ::
      StructField("Transcript start (bp)", LongType) ::
      StructField("Transcript end (bp)", LongType) ::
      StructField("Transcription start site (TSS)", LongType) ::
      StructField("Transcript length (including UTRs and CDS)", LongType) ::
      StructField("Gene name", StringType) ::
      StructField("Chromosome/scaffold name", StringType) ::
      StructField("Gene type", StringType) :: Nil)

  /** load and transform lut gene transcript gene name from ensembl mart website
    *
    * columns from the tsv file from ensembl
    * - Gene stable ID
    * - Transcript stable ID
    * - Gene start (bp)
    * - Gene end (bp)
    * - Transcript start (bp)
    * - Transcript end (bp)
    * - Transcription start site (TSS)
    * - Transcript length (including UTRs and CDS)
    * - Gene name
    * - Chromosome/scaffold name
    * - Gene type
    *
    * @param from mostly from config.ensembl.geneTranscriptPairs
    * @param ss implicit sparksession
    * @return the processed dataframe
    */
  def apply(from: String)(implicit ss: SparkSession): EnsemblIndex = {
    val transcripts = ss.read
      .format("csv")
      .option("header", "true")
      .option("inferSchema", "false")
      .option("delimiter","\t")
      .option("mode", "DROPMALFORMED")
      .schema(schema)
      .load(from)
      .withColumnRenamed("Gene stable ID", "gene_id")
      .withColumnRenamed("Transcript stable ID", "trans_id")
      .withColumnRenamed("Gene start (bp)", "gene_start")
      .withColumnRenamed("Gene end (bp)", "gene_end")
      .withColumnRenamed("Transcript start (bp)", "trans_start")
      .withColumnRenamed("Transcript end (bp)", "trans_end")
      .withColumnRenamed("Transcription start site (TSS)", "tss")
      .withColumnRenamed("Transcript length (including UTRs and CDS)", "trans_size")
      .withColumnRenamed("Gene name", "gene_name")
      .withColumnRenamed("Chromosome/scaffold name", "gene_chr")
      .withColumnRenamed("Gene type", "gene_type")

    new EnsemblIndex(buildPosSegment(transcripts,"gene_start", "gene_segment"))
  }
}
