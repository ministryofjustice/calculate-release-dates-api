-- Grant additional RDS roles required for replication to the DPR user if the roles exist.
DO $$
DECLARE
    dpr_user TEXT := '${dpr_user}';
    roles_to_add TEXT[] := ARRAY['rds_superuser','rds_replication'];
    role_to_add TEXT;
BEGIN
    FOREACH role_to_add IN ARRAY roles_to_add LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_to_add) THEN
            EXECUTE format('GRANT %I TO %I', role_to_add, dpr_user);
        END IF;
    END LOOP;
END $$;