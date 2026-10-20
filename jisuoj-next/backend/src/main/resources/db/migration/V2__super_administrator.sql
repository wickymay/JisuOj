-- Preserve access to sensitive settings during the role split: administrators
-- that already existed before this release become the initial super admins.
INSERT INTO `privilege` (`user_id`, `rightstr`, `valuestr`, `defunct`)
SELECT DISTINCT p.`user_id`, 'super_administrator', 'true', 'N'
FROM `privilege` p
WHERE p.`rightstr` = 'administrator'
  AND p.`defunct` = 'N'
  AND NOT EXISTS (
    SELECT 1
    FROM `privilege` existing
    WHERE existing.`user_id` = p.`user_id`
      AND existing.`rightstr` = 'super_administrator'
      AND existing.`defunct` = 'N'
  );
