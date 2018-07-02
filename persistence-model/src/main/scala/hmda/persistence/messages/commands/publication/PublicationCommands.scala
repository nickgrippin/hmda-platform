package hmda.persistence.messages.commands.publication

import hmda.model.fi.SubmissionId
import hmda.persistence.messages.CommonMessages.Command

object PublicationCommands {
  case class GenerateDisclosureReports2(institutionIds: List[String]) extends Command
  case class GenerateDisclosureReports(submissionId: SubmissionId) extends Command
  case class PublishIndividualReport(institutionId: String, msa: Int, report: String)
  case class GenerateSeriesOfReports(institutionId: String, reports: List[String])
  case class GenerateAggregateReports() extends Command
}
