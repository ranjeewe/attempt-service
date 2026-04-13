ALTER TABLE attempt.markingschema
    ADD COLUMN IF NOT EXISTS exam_paper_id INTEGER;

CREATE INDEX IF NOT EXISTS idx_markingschema_exam_paper_id
    ON attempt.markingschema (exam_paper_id);
