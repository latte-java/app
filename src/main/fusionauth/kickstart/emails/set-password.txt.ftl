Hi ${(user.firstName)!'there'},

You've been invited to join Latte Java. Visit the link below to set a password and finish creating your account.

${tenant.issuer}/password/change/${changePasswordId}?tenantId=${user.tenantId}&client_id=e9fdb985-9173-4e01-9d73-ac2d60d1dc8e&redirect_uri=${'http://localhost:8080/oidc/return'?url('UTF-8')}&response_type=code

You're receiving this because someone invited you to Latte Java. If you weren't expecting this, you can safely ignore this email.

— Latte Java
