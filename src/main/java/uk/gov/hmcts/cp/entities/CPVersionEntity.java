package uk.gov.hmcts.cp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cp_version")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CPVersionEntity {

    @Id
    @Column(name = "cp_version_pk")
    private UUID cpVersionPk;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "defendant_id")
    private UUID defendantId;

    @Column(name = "case_hearing_id")
    private UUID caseHearingId;

    @Column(name = "custody_location")
    private String custodyLocation;

    @Column(name = "master_defendant_id")
    private UUID masterDefendantId;

    @Embedded
    private CPNextHearingEmbeddable nextHearing;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    // Defendant PII (ADR-004/AMP-891) — every column is varchar regardless of the field's real
    // type, since the application layer will store ciphertext here, not a value Postgres could
    // parse natively. Plain String today: no EncryptionService is wired yet (ADR-004 scope).
    private String title;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Column(name = "address_line_1")
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column(name = "address_line_3")
    private String addressLine3;

    @Column(name = "address_line_4")
    private String addressLine4;

    @Column(name = "address_line_5")
    private String addressLine5;

    @Column(name = "post_code")
    private String postCode;
}
