package uk.gov.hmcts.cp.mappers;

import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.util.List;

public record CPVersionWriteBundle(
        CPVersionEntity version,
        List<CPCourtApplicationEntity> courtApplications,
        List<CPOffenceEntity> offences,
        List<CPJudicialResultEntity> judicialResults,
        List<CPJudicialResultPromptEntity> judicialResultPrompts) {
}