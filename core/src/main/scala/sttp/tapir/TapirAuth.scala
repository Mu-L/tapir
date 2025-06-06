package sttp.tapir

import sttp.model.HeaderNames
import sttp.model.headers.{AuthenticationScheme, WWWAuthenticateChallenge}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointInput.Auth

import scala.collection.immutable.ListMap
import sttp.tapir.EndpointIO.Header

object TapirAuth {

  /** Reads authorization data from the given `input`. */
  def apiKey[T](
      input: EndpointInput.Single[T],
      challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge("ApiKey")
  ): EndpointInput.Auth[T, EndpointInput.AuthType.ApiKey] =
    EndpointInput.Auth(input, challenge, EndpointInput.AuthType.ApiKey(), EndpointInput.AuthInfo.Empty)

  /** Reads authorization data from the `Authorization` headers starting with `Basic `, removing the prefix. To parse the data as a
    * base64-encoded username/password combination, use: `basic[UsernamePassword]`
    * @see
    *   UsernamePassword
    */
  def basic[T: Codec[List[String], *, CodecFormat.TextPlain]](
      challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.basic
  ): EndpointInput.Auth[T, EndpointInput.AuthType.Http] = http(AuthenticationScheme.Basic.name, challenge)

  /** Reads authorization data from the `Authorization` headers starting with `Bearer `, removing the prefix. */
  def bearer[T: Codec[List[String], *, CodecFormat.TextPlain]](
      challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
  ): EndpointInput.Auth[T, EndpointInput.AuthType.Http] = http(AuthenticationScheme.Bearer.name, challenge)

  def http[T: Codec[List[String], *, CodecFormat.TextPlain]](
      authScheme: String,
      challenge: WWWAuthenticateChallenge
  ): EndpointInput.Auth[T, EndpointInput.AuthType.Http] = {
    val codec = implicitly[Codec[List[String], T, CodecFormat.TextPlain]]

    def filterHeaders[T: Codec[List[String], *, TextPlain]](headers: List[String]) =
      headers.filter(_.toLowerCase.startsWith(authScheme.toLowerCase))

    def stringPrefixWithSpace: Mapping[List[String], List[String]] =
      Mapping.stringPrefixCaseInsensitiveForList(authScheme + " ")

    val authCodec = Codec
      .id[List[String], CodecFormat.TextPlain](codec.format, Schema.binary)
      .map(filterHeaders(_))(identity)
      .map(stringPrefixWithSpace)
      .mapDecode(codec.decode)(codec.encode)
      .schema(codec.schema)

    EndpointInput.Auth(
      header[T](HeaderNames.Authorization)(authCodec),
      challenge,
      EndpointInput.AuthType.Http(authScheme),
      EndpointInput.AuthInfo.Empty
    )
  }

  object oauth2 {
    sealed trait OAuth2Flow
    object OAuth2Flow {
      case object AuthenticationCode extends OAuth2Flow
      case object ClientCredentials extends OAuth2Flow
      case object Implicit extends OAuth2Flow

      val Attribute: AttributeKey[OAuth2Flow] = new AttributeKey[OAuth2Flow]("sttp.tapir.TapirAuth.oauth2.OAuth2Flow")
    }

    private val bearerMapping: Mapping[String, String] = Mapping.stringPrefixCaseInsensitive(AuthenticationScheme.Bearer.name + " ")

    private val oauthInputRequired: Header[String] = header[String](HeaderNames.Authorization).map(bearerMapping)
    private val oauthInputOptional: Header[Option[String]] = header[Option[String]](HeaderNames.Authorization)
      .mapDecode[Option[String]](opt => opt.map(v => bearerMapping.decode(v).map(Some(_))).getOrElse(DecodeResult.Value(None)))(
        _.map(bearerMapping.encode)
      )

    @deprecated("Use instead authorizationCodeFlow, clientCredentialsFlow or implicitFlow", "")
    def authorizationCode(
        authorizationUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        tokenUrl: Option[String] = None,
        refreshUrl: Option[String] = None,
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[String, EndpointInput.AuthType.OAuth2] = {
      EndpointInput.Auth(
        oauthInputRequired,
        challenge,
        EndpointInput.AuthType.OAuth2(authorizationUrl, tokenUrl, scopes, refreshUrl),
        EndpointInput.AuthInfo.Empty
      )
    }

    private def buildInput[T](
        baseOAuth: EndpointInput.AuthType.OAuth2,
        challenge: WWWAuthenticateChallenge,
        flow: OAuth2Flow,
        input: Header[T]
    ): Auth[T, EndpointInput.AuthType.OAuth2] =
      EndpointInput
        .Auth(input, challenge, baseOAuth: EndpointInput.AuthType.OAuth2, EndpointInput.AuthInfo.Empty)
        .attribute(OAuth2Flow.Attribute, flow)

    def authorizationCodeFlow(
        authorizationUrl: String,
        tokenUrl: String,
        refreshUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[String, EndpointInput.AuthType.OAuth2] = buildInput(
      EndpointInput.AuthType.OAuth2(Some(authorizationUrl), Some(tokenUrl), scopes, refreshUrl),
      challenge,
      OAuth2Flow.AuthenticationCode,
      oauthInputRequired
    )

    def authorizationCodeFlowOptional(
        authorizationUrl: String,
        tokenUrl: String,
        refreshUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[Option[String], EndpointInput.AuthType.OAuth2] = buildInput(
      EndpointInput.AuthType.OAuth2(Some(authorizationUrl), Some(tokenUrl), scopes, refreshUrl),
      challenge,
      OAuth2Flow.AuthenticationCode,
      oauthInputOptional
    )

    def clientCredentialsFlow(
        tokenUrl: String,
        refreshUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[String, EndpointInput.AuthType.OAuth2] =
      buildInput(
        EndpointInput.AuthType.OAuth2(None, Some(tokenUrl), scopes, refreshUrl),
        challenge,
        OAuth2Flow.ClientCredentials,
        oauthInputRequired
      )

    def clientCredentialsFlowOptional(
        tokenUrl: String,
        refreshUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[Option[String], EndpointInput.AuthType.OAuth2] =
      buildInput(
        EndpointInput.AuthType.OAuth2(None, Some(tokenUrl), scopes, refreshUrl),
        challenge,
        OAuth2Flow.ClientCredentials,
        oauthInputOptional
      )

    def implicitFlow(
        authorizationUrl: String,
        refreshUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[String, EndpointInput.AuthType.OAuth2] =
      buildInput(
        EndpointInput.AuthType.OAuth2(Some(authorizationUrl), None, scopes, refreshUrl),
        challenge,
        OAuth2Flow.Implicit,
        oauthInputRequired
      )

    def implicitFlowOptional(
        authorizationUrl: String,
        refreshUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[Option[String], EndpointInput.AuthType.OAuth2] =
      buildInput(
        EndpointInput.AuthType.OAuth2(Some(authorizationUrl), None, scopes, refreshUrl),
        challenge,
        OAuth2Flow.Implicit,
        oauthInputOptional
      )
  }
}
