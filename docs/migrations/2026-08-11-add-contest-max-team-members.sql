-- Add the contest-level team size limit before deploying the application
-- version that maps Contest.maxTeamMembers.
--
-- Existing individual contests are fixed at one participant. TEAM and BOTH
-- contests retain the previous application-wide limit of five participants.

ALTER TABLE contest
    ADD COLUMN max_team_members INT NULL AFTER participation_type;

UPDATE contest
SET max_team_members = CASE
    WHEN participation_type = 'INDIVIDUAL' THEN 1
    ELSE 5
END;

ALTER TABLE contest
    MODIFY COLUMN max_team_members INT NOT NULL,
    ADD CONSTRAINT chk_contest_max_team_members
        CHECK (
            max_team_members >= 1
            AND (
                participation_type <> 'INDIVIDUAL'
                OR max_team_members = 1
            )
        );
