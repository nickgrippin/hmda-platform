package hmda.api.http.codec.filing.submission

import hmda.api.http.model.filing.submissions.{
  EditDetailsSummary,
  PaginatedResponse,
  PaginationLinks
}
import hmda.model.edits.EditDetailsRow
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.syntax._
import io.circe.generic.auto._

object EditDetailsSummaryCodec {

  implicit val editDetailsSummaryEncoder: Encoder[EditDetailsSummary] =
    new Encoder[EditDetailsSummary] {
      override def apply(a: EditDetailsSummary): Json = Json.obj(
        ("edit", Json.fromString(a.editName)),
        ("rows", Json.arr(a.rows.asJson)),
        ("count", Json.fromInt(a.count)),
        ("total", Json.fromInt(a.total)),
        ("_links", a.links.asJson)
      )
    }

  implicit val editDetailsSummaryDecoder: Decoder[EditDetailsSummary] =
    new Decoder[EditDetailsSummary] {
      override def apply(c: HCursor): Result[EditDetailsSummary] =
        for {
          edit <- c.downField("edit").as[String]
          rows <- c.downField("rows").as[Seq[EditDetailsRow]]
          total <- c.downField("total").as[Int]
          links <- c.downField("_links").as[PaginationLinks]
        } yield {
          val path = PaginatedResponse.staticPath(links.href)
          val currentPage = PaginatedResponse.currentPage(links.self)
          EditDetailsSummary(
            edit,
            rows,
            path,
            currentPage,
            total
          )
        }
    }

  implicit val editDetailsRowEncoder: Encoder[EditDetailsRow] =
    new Encoder[EditDetailsRow] {
      override def apply(a: EditDetailsRow): Json = Json.obj(
        ("id", Json.fromString(a.id))
        //TODO: Include field details
      )
    }

  implicit val editDetailsRowDecoder: Decoder[EditDetailsRow] =
    new Decoder[EditDetailsRow] {
      override def apply(c: HCursor): Result[EditDetailsRow] =
        for {
          id <- c.downField("id").as[String]
        } yield EditDetailsRow(id)
    }

}
