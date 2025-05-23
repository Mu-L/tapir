package sttp.tapir

import sttp.tapir.SchemaType._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.TestUtil.field

class SchemaTest extends AnyFlatSpec with Matchers {
  it should "modify basic schema" in {
    implicitly[Schema[String]].modifyUnsafe[String]()(_.description("test")) shouldBe implicitly[Schema[String]]
      .copy(description = Some("test"))
  }

  it should "modify product schema" in {
    val name1 = SName("X")
    Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("f2"), Schema(SString())))), Some(name1))
      .modifyUnsafe[String]("f2")(_.description("test").default("f2").encodedExample("f2_example")) shouldBe Schema(
      SProduct[Unit](
        List(
          field(FieldName("f1"), Schema(SInteger())),
          field(FieldName("f2"), Schema(SString()).description("test").default("f2").encodedExample("f2_example"))
        )
      ),
      Some(name1)
    )
  }

  it should "modify nested product schema" in {
    val name1 = SName("X")
    val name2 = SName("Y")

    val nestedProduct =
      Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("f2"), Schema(SString())))), Some(name2))
    val expectedNestedProduct =
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f1"), Schema(SInteger())),
            field(FieldName("f2"), Schema(SString()).description("test").default("f2").encodedExample("f2_example"))
          )
        ),
        Some(name2)
      )

    Schema(
      SProduct[Unit](
        List(field(FieldName("f3"), Schema(SString())), field(FieldName("f4"), nestedProduct), field(FieldName("f5"), Schema(SBoolean())))
      ),
      Some(name1)
    )
      .modifyUnsafe[String]("f4", "f2")(_.description("test").default("f2").encodedExample("f2_example")) shouldBe
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f3"), Schema(SString())),
            field(FieldName("f4"), expectedNestedProduct),
            field(FieldName("f5"), Schema(SBoolean()))
          )
        ),
        Some(name1)
      )
  }

  it should "modify array elements in products" in {
    val name1 = SName("X")
    Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable))))), Some(name1))
      .modifyUnsafe[String]("f1", Schema.ModifyCollectionElements)(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema[String](SString()).format("xyz"))(_.toIterable))))
      ),
      Some(name1)
    )
  }

  it should "modify array in products" in {
    val name1 = SName("X")
    Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable))))), Some(name1))
      .modifyUnsafe[String]("f1")(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable)).format("xyz")))
      ),
      Some(name1)
    )
  }

  it should "modify property of optional parameter" in {
    val name1 = SName("X")
    val name2 = SName("Y")
    Schema(
      SProduct[Unit](
        List(field(FieldName("f1"), Schema(SProduct[Unit](List(field(FieldName("p1"), Schema(SInteger()).asOption))), Some(name2))))
      ),
      Some(name1)
    )
      .modifyUnsafe[Int]("f1", "p1")(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        List(
          field(
            FieldName("f1"),
            Schema(SProduct[Unit](List(field(FieldName("p1"), Schema(SInteger()).asOption.format("xyz")))), Some(name2))
          )
        )
      ),
      Some(name1)
    )
  }

  it should "modify property of map value" in {
    Schema(
      SOpenProduct[Map[String, Unit], Unit](
        Nil,
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("X")))
      )(identity),
      Some(SName("Map", List("X")))
    )
      .modifyUnsafe[Int](Schema.ModifyCollectionElements)(_.description("test")) shouldBe Schema(
      SOpenProduct[Map[String, Unit], Unit](
        Nil,
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("X"))).description("test")
      )(identity),
      Some(SName("Map", List("X")))
    )
  }

  it should "modify open product schema" in {
    val openProductSchema =
      Schema(
        SOpenProduct[Map[String, Unit], Unit](
          Nil,
          Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("X")))
        )(_ => Map.empty),
        Some(SName("Map", List("X")))
      )
    openProductSchema
      .modifyUnsafe[Nothing]()(_.description("test")) shouldBe openProductSchema.description("test")
  }

  it should "generate one-of schema using the given discriminator" in {
    val coproduct = SCoproduct[Unit](
      List(
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("H"))),
        Schema(
          SProduct[Unit](List(field(FieldName("f1"), Schema(SString())), field(FieldName("f2"), Schema(SString())))),
          Some(SName("G"))
        ),
        Schema(SString[Unit]())
      ),
      None
    )(_ => None)

    val coproduct2 = coproduct.addDiscriminatorField(FieldName("who_am_i"))

    coproduct2.subtypes shouldBe List(
      Schema(
        SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("who_am_i"), Schema(SString())))),
        Some(SName("H"))
      ),
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f1"), Schema(SString())),
            field(FieldName("f2"), Schema(SString())),
            field(FieldName("who_am_i"), Schema(SString()))
          )
        ),
        Some(SName("G"))
      ),
      Schema(SString[Unit]())
    )

    coproduct2.discriminator shouldBe Some(SDiscriminator(FieldName("who_am_i"), Map.empty))
  }

  it should "addDiscriminatorField should only add discriminator field to child schemas if not yet present" in {
    val coproduct = SCoproduct[Unit](
      List(
        Schema(SProduct[Unit](List(field(FieldName("f0"), Schema(SString())))), Some(SName("H"))),
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SString())))), Some(SName("G")))
      ),
      None
    )(_ => None)

    val coproduct2 = coproduct.addDiscriminatorField(FieldName("f0"))

    coproduct2.subtypes shouldBe List(
      Schema(SProduct[Unit](List(field(FieldName("f0"), Schema(SString())))), Some(SName("H"))),
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f1"), Schema(SString())),
            field(FieldName("f0"), Schema(SString()))
          )
        ),
        Some(SName("G"))
      )
    )

    coproduct2.discriminator shouldBe Some(SDiscriminator(FieldName("f0"), Map.empty))
  }

  it should "propagate format for optional schemas" in {
    implicitly[Schema[Option[Double]]].format shouldBe Some("double")
  }

  case class SomeValueString[A](value: String, v2: A)
  final case class SomeValueInt(value: Int)
  final case class Node[A](values: List[A])

  it should "generate correct names for generic classes with semi-automatic derivation and parameterized types" in {
    implicit def schemaSomeValueString[A](implicit a: Schema[A]): Schema[SomeValueString[A]] =
      Schema.derived[SomeValueString[A]].renameWithTypeParameter[A]
    implicit def SomeValueInt: Schema[SomeValueInt] = Schema.derived[SomeValueInt]
    implicit def schemaNode[A](implicit a: Schema[A]): Schema[Node[A]] = Schema.derived[Node[A]].renameWithTypeParameter[A]

    implicitly[Schema[Node[SomeValueInt]]].name shouldBe Some(
      SName(
        "sttp.tapir.SchemaTest.Node",
        List("sttp.tapir.SchemaTest.SomeValueInt")
      )
    )
    implicitly[Schema[Node[SomeValueString[SomeValueInt]]]].name shouldBe Some(
      SName(
        "sttp.tapir.SchemaTest.Node",
        List("sttp.tapir.SchemaTest.SomeValueString", "sttp.tapir.SchemaTest.SomeValueInt")
      )
    )
  }

  it should "generate correct names for Eithers with parameterized types" in {
    import sttp.tapir.generic.auto._
    implicitly[Schema[Either[Int, Int]]].name shouldBe None
    implicitly[Schema[Either[SomeValueInt, Int]]].name shouldBe None
    implicitly[Schema[Either[SomeValueInt, SomeValueInt]]].name shouldBe Some(
      SName("Either", List("sttp.tapir.SchemaTest.SomeValueInt", "sttp.tapir.SchemaTest.SomeValueInt"))
    )
    implicitly[Schema[Either[SomeValueInt, Node[SomeValueString[Boolean]]]]].name shouldBe Some(
      SName(
        "Either",
        List("sttp.tapir.SchemaTest.SomeValueInt", "sttp.tapir.SchemaTest.Node", "sttp.tapir.SchemaTest.SomeValueString", "scala.Boolean")
      )
    )
    implicitly[Schema[Either[SomeValueInt, Node[String]]]].name shouldBe Some(
      SName("Either", List("sttp.tapir.SchemaTest.SomeValueInt", "sttp.tapir.SchemaTest.Node", "java.lang.String"))
    )
    implicitly[Schema[Either[Node[Boolean], SomeValueInt]]].name shouldBe Some(
      SName("Either", List("sttp.tapir.SchemaTest.Node", "scala.Boolean", "sttp.tapir.SchemaTest.SomeValueInt"))
    )
  }

  it should "generate correct names for Maps with parameterized types" in {
    import sttp.tapir.generic.auto._
    type Tree[A] = Either[A, Node[A]]
    val schema1: Schema[Map[SomeValueInt, Node[SomeValueString[Boolean]]]] = Schema.schemaForMap(_.toString)
    schema1.name shouldBe Some(
      SName(
        "Map",
        List("sttp.tapir.SchemaTest.SomeValueInt", "sttp.tapir.SchemaTest.Node", "sttp.tapir.SchemaTest.SomeValueString", "scala.Boolean")
      )
    )
    val schema2: Schema[Map[Node[Boolean], Node[String]]] = Schema.schemaForMap(_.toString)
    schema2.name shouldBe Some(
      SName("Map", List("sttp.tapir.SchemaTest.Node", "scala.Boolean", "sttp.tapir.SchemaTest.Node", "java.lang.String"))
    )
    val schema3: Schema[Map[Int, Tree[String]]] = Schema.schemaForMap(_.toString)
    schema3.name shouldBe Some(
      SName("Map", List("scala.Int", "scala.util.Either", "java.lang.String", "sttp.tapir.SchemaTest.Node", "java.lang.String"))
    )
    val schema4: Schema[Map[Tree[String], Int]] = Schema.schemaForMap(_.toString)
    schema4.name shouldBe Some(
      SName("Map", List("scala.util.Either", "java.lang.String", "sttp.tapir.SchemaTest.Node", "java.lang.String", "scala.Int"))
    )
  }

}
