CREATE SCHEMA IF NOT EXISTS attempt;

CREATE SEQUENCE IF NOT EXISTS attempt.attempt_id_seq;
CREATE SEQUENCE IF NOT EXISTS attempt.attemptquestion_id_seq;
CREATE SEQUENCE IF NOT EXISTS attempt.attemptanswer_id_seq;
CREATE SEQUENCE IF NOT EXISTS attempt.markingschema_id_seq;
CREATE SEQUENCE IF NOT EXISTS attempt.ms_question_id_seq;
CREATE SEQUENCE IF NOT EXISTS attempt.correctanswer_id_seq;

CREATE TABLE attempt.attempt (
    id INTEGER NOT NULL DEFAULT nextval('attempt.attempt_id_seq') PRIMARY KEY,
    exam_paper_id INTEGER
);

CREATE TABLE attempt.attemptquestion (
    id INTEGER NOT NULL DEFAULT nextval('attempt.attemptquestion_id_seq') PRIMARY KEY,
    attemptid INTEGER NOT NULL,
    question_number INTEGER,
    CONSTRAINT fk_attemptquestion_attempt FOREIGN KEY (attemptid) REFERENCES attempt.attempt(id)
);

CREATE TABLE attempt.attemptanswer (
    id INTEGER NOT NULL DEFAULT nextval('attempt.attemptanswer_id_seq') PRIMARY KEY,
    questionid INTEGER NOT NULL,
    option_number INTEGER,
    answer VARCHAR(255),
    correct BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT fk_attemptanswer_attemptquestion FOREIGN KEY (questionid) REFERENCES attempt.attemptquestion(id)
);

CREATE TABLE attempt.markingschema (
    id INTEGER NOT NULL DEFAULT nextval('attempt.markingschema_id_seq') PRIMARY KEY
);

CREATE TABLE attempt.question (
    id INTEGER NOT NULL DEFAULT nextval('attempt.ms_question_id_seq') PRIMARY KEY,
    markingschemaid INTEGER NOT NULL,
    question_number INTEGER,
    CONSTRAINT fk_ms_question_markingschema FOREIGN KEY (markingschemaid) REFERENCES attempt.markingschema(id)
);

CREATE TABLE attempt.correctanswer (
    id INTEGER NOT NULL DEFAULT nextval('attempt.correctanswer_id_seq') PRIMARY KEY,
    questionid INTEGER NOT NULL,
    option_number INTEGER,
    CONSTRAINT fk_correctanswer_question FOREIGN KEY (questionid) REFERENCES attempt.question(id)
);
