CREATE TABLE setting (
	uuid UUID NOT NULL,
	i_author VARCHAR NOT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	"section" TEXT NOT NULL,
	category TEXT NULL,
	"name" text NOT NULL,
	"value" TEXT NULL
);