package sttp.tapir.docs.apispec.schema

import io.circe.literal._
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.circe._
import sttp.apispec.{Schema => ASchema}
import sttp.tapir.Schema.annotations.title
import sttp.tapir._
import sttp.tapir.generic.auto._

class TapirSchemaToJsonSchemaTest extends AnyFlatSpec with Matchers with OptionValues with EitherValues with Inside {
  behavior of "TapirSchemaToJsonSchema"

  it should "represent schema as JSON" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childId: String, childNames: List[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result: ASchema = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","title":"Parent","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}},"$$defs":{"Child":{"title":"Child","required":["childId"],"type":"object","properties":{"childId":{"type":"string"},"childNames":{"type":"array","items":{"type":"string"}}}}}}"""

  }

  it should "handle top-level simple schemas" in {
    // given
    val tSchema = implicitly[Schema[List[Int]]]

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","type":"array","items":{"type":"integer","format":"int32"}}"""
  }

  it should "handle repeated type names" in {
    // given
    object Childhood {
      case class Child(age: Int, height: Option[Int])
    }
    case class Parent(innerChildField: Child, childDetails: Childhood.Child)
    case class Child(childName: String)
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","title":"Parent","required":["innerChildField","childDetails"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"},"childDetails":{"$$ref":"#/$$defs/Child1"}},"$$defs":{"Child":{"title":"Child","required":["childName"],"type":"object","properties":{"childName":{"type":"string"}}},"Child1":{"title":"Child","required":["age"],"type":"object","properties":{"age":{"type":"integer","format":"int32"},"height":{"type":["integer", "null"],"format":"int32"}}}}}"""
  }

  it should "handle options as not nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = false)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","title":"Parent","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}},"$$defs":{"Child":{"title":"Child","type":"object","properties":{"childName":{"type":"string"}}}}}"""

  }

  it should "handle options as nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","title":"Parent","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}},"$$defs":{"Child":{"title":"Child","type":"object","properties":{"childName":{"type":["string","null"]}}}}}"""
  }

  it should "use title from annotation or ref name" in {
    // given
    @title("MyOwnTitle1")
    case class Outer(inner: Parent)

    case class Parent(innerChildField: Child)

    @title("MyOwnTitle3")
    case class Child(childName: Option[String])

    val tSchema = implicitly[Schema[Outer]]

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","title":"MyOwnTitle1","required":["inner"],"type":"object","properties":{"inner":{"$$ref":"#/$$defs/Parent"}},"$$defs":{"Parent":{"title":"Parent","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}}},"Child":{"title":"MyOwnTitle3","type":"object","properties":{"childName":{"type":["string","null"]}}}}}"""
  }

  it should "NOT use generate default titles if disabled" in {
    // given
    case class Parent(innerChildField: Child)

    @title("MyChild")
    case class Child(childName: Option[String])

    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","title":"Parent","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}},"$$defs":{"Child":{"title":"MyChild","type":"object","properties":{"childName":{"type":["string","null"]}}}}}"""
  }

  it should "Generate correct names for Eithers with parameterized types" in {
    case class SomeValueString[A](value: String, v2: A)
    final case class SomeValueInt(value: Int)

    final case class Node[A](values: List[A])

    TapirSchemaToJsonSchema(implicitly[Schema[Either[Int, Int]]], true).title shouldBe None
    TapirSchemaToJsonSchema(implicitly[Schema[Either[SomeValueInt, Int]]], true).title shouldBe None
    TapirSchemaToJsonSchema(implicitly[Schema[Either[SomeValueInt, SomeValueInt]]], true).title shouldBe Some(
      "Either_SomeValueInt_SomeValueInt"
    )
    TapirSchemaToJsonSchema(implicitly[Schema[Either[SomeValueInt, Node[SomeValueString[Boolean]]]]], true).title shouldBe Some(
      "Either_SomeValueInt_Node_SomeValueString_Boolean"
    )
    TapirSchemaToJsonSchema(implicitly[Schema[Either[SomeValueInt, Node[String]]]], true).title shouldBe Some(
      "Either_SomeValueInt_Node_String"
    )
    TapirSchemaToJsonSchema(implicitly[Schema[Either[Node[Boolean], SomeValueInt]]], true).title shouldBe Some(
      "Either_Node_Boolean_SomeValueInt"
    )
  }

  it should "Generate correct names for Maps with parameterized types" in {
    type Tree[A] = Either[A, Node[A]]
    final case class Node[A](values: List[A])
    val schema1: Schema[Map[Int, Tree[String]]] = Schema.schemaForMap(_.toString)
    TapirSchemaToJsonSchema(schema1, true).title shouldBe Some("Map_Int_Either_String_Node_String")
    val schema2: Schema[Map[Tree[Int], String]] = Schema.schemaForMap(_.toString)
    TapirSchemaToJsonSchema(schema2, true).title shouldBe Some("Map_Either_Int_Node_Int_String")
  }

  it should "Generate array for products marked with Tuple attribute" in {
    val tSchema: Schema[(Int, String)] = implicitly[Schema[(Int, String)]]

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = false)

    // then
    result.asJson.deepDropNullValues shouldBe
      json"""{"$$schema" : "http://json-schema.org/draft/2020-12/schema#", "title" : "Tuple2_Int_String", "type" : "array", "prefixItems" : [{"type" : "integer", "format" : "int32"}, {"type" : "string"}]}"""
  }

  it should "handle as nullable if marked with Nullable attribute" in {
    // given
    case class Parent(innerChildField: Child, nullableInnerChild: Child)
    case class Child(childName: Option[String])

    val tSchema =
      implicitly[Schema[Parent]]
        .modify(_.innerChildField.childName)(_.nullable)
        .modify(_.nullableInnerChild)(_.nullable)

    // when
    val result = TapirSchemaToJsonSchema(tSchema, markOptionsAsNullable = false)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"http://json-schema.org/draft/2020-12/schema#","title":"Parent","required":["innerChildField","nullableInnerChild"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"},"nullableInnerChild":{"title":"Child","anyOf":[{"$$ref":"#/$$defs/Child"},{"type":"null"}]}},"$$defs":{"Child":{"title":"Child","type":"object","properties":{"childName":{"type":["string","null"]}}}}}"""
  }

  it should "generate a top-level array schema" in {
    // given
    case class Test(i: Int)

    // when
    val result: ASchema = TapirSchemaToJsonSchema(implicitly[Schema[List[Test]]], markOptionsAsNullable = true)

    // then
    result.asJson shouldBe json"""{
      "$$schema" : "http://json-schema.org/draft/2020-12/schema#",
      "$$defs" : {
        "Test" : {
          "title" : "Test",
          "type" : "object",
          "required" : [
            "i"
          ],
          "properties" : {
            "i" : {
              "type" : "integer",
              "format" : "int32"
            }
          }
        }
      },
      "type" : "array",
      "items" : {
        "$$ref" : "#/$$defs/Test"
      }
    }"""
  }

  it should "generate a top-level recursive schema" in {
    // given
    case class Test(i: Int, children: List[Test])
    implicit lazy val schema: Schema[Test] = Schema.derived[Test]

    // when
    val result: ASchema = TapirSchemaToJsonSchema(implicitly[Schema[Test]], markOptionsAsNullable = true)

    // then
    result.asJson shouldBe json"""{
      "$$schema" : "http://json-schema.org/draft/2020-12/schema#",
      "$$defs" : {
        "Test" : {
          "title" : "Test",
          "type" : "object",
          "required" : [
            "i"
          ],
          "properties" : {
            "i" : {
              "type" : "integer",
              "format" : "int32"
            },
            "children" : {
              "type" : "array",
              "items" : {
                "$$ref" : "#/$$defs/Test"
              }
            }
          }
        }
      },
      "$$ref" : "#/$$defs/Test"
    }"""
  }

  it should "generate a top-level recursive schema with coproducts" in {
    // given
    sealed trait Model
    final case class SetModel(set: Set[Model]) extends Model
    final case class LeafModel(num: Int) extends Model

    implicit lazy val schema: Schema[Model] = Schema.derived[Model]

    // when
    val result: ASchema = TapirSchemaToJsonSchema(implicitly[Schema[Model]], markOptionsAsNullable = true)

    // then
    result.asJson shouldBe json"""{
      "$$schema" : "http://json-schema.org/draft/2020-12/schema#",
      "$$ref" : "#/$$defs/Model",
      "$$defs" : {
        "Model" : {
          "title" : "Model",
          "oneOf" : [
            {
              "$$ref" : "#/$$defs/LeafModel"
            },
            {
              "$$ref" : "#/$$defs/SetModel"
            }
          ]
        },
        "LeafModel" : {
          "title" : "LeafModel",
          "type" : "object",
          "required" : [
            "num"
          ],
          "properties" : {
            "num" : {
              "type" : "integer",
              "format" : "int32"
            }
          }
        },
        "SetModel" : {
          "title" : "SetModel",
          "type" : "object",
          "properties" : {
            "set" : {
              "type" : "array",
              "uniqueItems" : true,
              "items" : {
                "$$ref" : "#/$$defs/Model"
              }
            }
          }
        }
      }
    }"""
  }
}
