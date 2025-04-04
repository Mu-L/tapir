package sttp.tapir.serverless.aws.cdk.test

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import io.circe.generic.auto._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.{AwsCatsEffectServerOptions, AwsRequestV1, LambdaHandler}

import java.io.{InputStream, OutputStream}

class IOLambdaHandlerV1 extends LambdaHandler[IO, AwsRequestV1](AwsCatsEffectServerOptions.noEncoding[IO]) {

  override protected def getAllEndpoints: List[ServerEndpoint[Any, IO]] = TestEndpoints.all[IO].toList

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    process(input, output).unsafeRunSync()
}
