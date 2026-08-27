-- Whether the connector said this run can be stopped and resumed, as declared at initiate and refreshed by each
-- resume. NULL for a v1 run, which cannot be stopped at all; the detail publishes that as false.
ALTER TABLE "discovery" ADD COLUMN "stoppable" BOOLEAN;
