DO $$
BEGIN
    IF EXISTS (
        SELECT LOWER(BTRIM(email))
        FROM users
        GROUP BY LOWER(BTRIM(email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate emails exist after case normalization. Resolve them before applying V7.';
END IF;
END
$$;

CREATE UNIQUE INDEX uk_users_email_normalized
    ON users (LOWER(BTRIM(email)));