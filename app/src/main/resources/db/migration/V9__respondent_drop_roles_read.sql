-- Respondents should not receive forms.roles.read (it opened Administration in the portal).
DELETE FROM forms_role_permission rp
USING forms_role r
WHERE rp.role_id = r.id
  AND r.code = 'RESPONDENT'
  AND r.system_defined IS TRUE
  AND rp.permission_code = 'forms.roles.read';
