-- Publishers who collect responses should also read reports and request exports.
INSERT INTO forms_role_permission (role_id, permission_code)
SELECT r.id, p.permission_code
FROM forms_role r
CROSS JOIN (
    VALUES
        ('forms.reports.read'),
        ('forms.exports.create')
) AS p(permission_code)
WHERE r.code = 'PUBLISHER'
  AND r.system_defined = TRUE
ON CONFLICT DO NOTHING;
