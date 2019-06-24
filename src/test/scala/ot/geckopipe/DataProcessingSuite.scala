package ot.geckopipe

import java.util.UUID

import minitest.SimpleTestSuite
import org.apache.spark.sql.SparkSession
import ot.geckopipe.domain.{Gene, RawVariant, Variant, Vep}

object DataProcessingSuite extends SimpleTestSuite {

  private val configuration = createTestConfiguration()
  private implicit val spark: SparkSession = SparkSession.builder().master("local").getOrCreate()

  import spark.implicits._

  test("calculate variant index") {
    Seq(
      ("1", 1000, "1", 1100, "A", "T", "rs123", Vep(most_severe_consequence = "severe consequence"), "cadd 1", "af 1")
    ).map(RawVariant.tupled(_)).toDF().write.parquet(configuration.variantIndex.raw)
    Seq(
      ("1", "ENSG00000223972", 11869, 11869, 14412, "protein_coding")
    ).map(Gene.tupled(_)).toDF().write.json(configuration.ensembl.lut)

    Main.run(CommandLineArgs(command = Some("variant-index")), configuration)

    def variants = spark.read.parquet(configuration.variantIndex.path).as[Variant]
    assertEquals(variants.collect().toList, List(
      Variant("1", 1100, "1", 1000, "A", "T", "rs123", "severe consequence", "cadd 1", "af 1", 10769L, "ENSG00000223972",
        10769L, "ENSG00000223972")
    ))
  }

  private def createTestConfiguration(): Configuration = {
    val uuid = UUID.randomUUID().toString

    val testDataFolder = s"/tmp/tests-$uuid"
    val inputFolder = s"$testDataFolder/input"
    val outputFolder = s"$testDataFolder/output"

    Configuration(
      output = outputFolder,
      sampleFactor = 0, //disabled
      sparkUri = "", //empty string for local
      logLevel = "INFO",
      ensembl = EnsemblSection(lut = s"$inputFolder/hg38.json"),
      vep = VEPSection(homoSapiensConsScores = s"$inputFolder/vep_consequences.tsv"),
      interval = IntervalSection(path = s"$inputFolder/v2g/interval/*/*/data.parquet/"),
      qtl = QTLSection(path = s"$inputFolder/v2g/qtl/*/*/data.parquet/"),
      nearest = NearestSection(tssDistance = 500000, path = s"$inputFolder/distance/canonical_tss/"),
      variantIndex =
        VariantSection(
          raw = s"$inputFolder/variant-annotation.parquet/",
          path = s"$outputFolder/variant-index/",
          tssDistance = 500000),
      variantGene = VariantGeneSection(path = s"$outputFolder/v2g/"),
      variantDisease =
        VariantDiseaseSection(
          path = s"$outputFolder/v2d/",
          studies = s"$inputFolder/v2d/studies.parquet",
          toploci = s"$inputFolder/v2d/toploci.parquet",
          finemapping = s"$inputFolder/v2d/finemapping.parquet",
          ld = s"$inputFolder/v2d/ld.parquet",
          overlapping = s"$inputFolder/v2d/locus_overlap.parquet",
          coloc = s"$inputFolder/coloc/010101/"))
  }
}
