package ot.geckopipe

import org.apache.spark.sql.{DataFrame, SparkSession}

object Ensembl {
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
  def loadEnsemblG2T(from: String)(implicit ss: SparkSession): DataFrame = {
    val transcripts = ss.read
      .format("csv")
      .option("header", "true")
      .option("inferSchema", "true")
      .option("delimiter","\t")
      .option("mode", "DROPMALFORMED")
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
      .toDF

    transcripts
  }
}
