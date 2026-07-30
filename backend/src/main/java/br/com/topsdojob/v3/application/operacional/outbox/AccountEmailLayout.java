package br.com.topsdojob.v3.application.operacional.outbox;

final class AccountEmailLayout {
  private static final String AUTOMATED_MESSAGE =
      "Mensagem automática. Não envie documentos, senhas ou dados pessoais por resposta.";

  private final String logoUrl;

  AccountEmailLayout(String canonicalDomain) {
    this.logoUrl = canonicalDomain + "/logo-email.webp";
  }

  Rendered render(
      String subject,
      String heading,
      String lead,
      String code,
      String expiry,
      String actionLabel,
      String actionUrl,
      String securityNotice,
      String ignoreNotice) {
    String text = """
        %s

        %s

        Código: %s
        %s

        Segurança: %s
        %s

        %s: %s

        %s
        """.formatted(
        heading,
        lead,
        code,
        expiry,
        securityNotice,
        ignoreNotice,
        actionLabel,
        actionUrl,
        AUTOMATED_MESSAGE).strip();

    String html = """
        <!doctype html>
        <html lang="pt-BR">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
          </head>
          <body style="margin:0;padding:0;background:#f4f4f5;color:#18181b;font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%%;">
            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background:#f4f4f5;">
              <tr>
                <td align="center" style="padding:24px 12px;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" data-account-email-layout="v1" style="width:100%%;max-width:600px;background:#ffffff;border:1px solid #e4e4e7;border-radius:8px;">
                    <tr>
                      <td align="center" style="padding:28px 24px 18px;">
                        <img src="%s" width="185" alt="Tops do Job" style="display:block;width:185px;max-width:100%%;height:auto;border:0;outline:none;text-decoration:none;">
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 32px 32px;">
                        <h1 style="margin:0 0 14px;font-size:24px;line-height:1.25;color:#18181b;text-align:center;">%s</h1>
                        <p style="margin:0 0 22px;font-size:16px;line-height:1.6;color:#3f3f46;text-align:center;">%s</p>
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                          <tr>
                            <td align="center" style="padding:18px 12px;background:#fdf2f8;border:1px solid #fbcfe8;border-radius:6px;">
                              <span x-apple-data-detectors="false" style="font-family:Arial,Helvetica,sans-serif;font-size:30px;line-height:1;font-weight:700;letter-spacing:6px;color:#be0868;text-decoration:none;white-space:nowrap;">%s</span>
                            </td>
                          </tr>
                        </table>
                        <p style="margin:14px 0 24px;font-size:14px;line-height:1.5;color:#52525b;text-align:center;">%s</p>
                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center">
                          <tr>
                            <td align="center" bgcolor="#FC1EAD" style="border-radius:6px;">
                              <a href="%s" style="display:inline-block;padding:13px 22px;font-size:15px;line-height:1.2;font-weight:700;color:#ffffff;text-decoration:none;border-radius:6px;">%s</a>
                            </td>
                          </tr>
                        </table>
                        <p style="margin:26px 0 8px;font-size:14px;line-height:1.55;color:#3f3f46;"><strong>Segurança:</strong> %s</p>
                        <p style="margin:0;font-size:14px;line-height:1.55;color:#52525b;">%s</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:18px 24px;background:#fafafa;border-top:1px solid #e4e4e7;text-align:center;">
                        <p style="margin:0;font-size:12px;line-height:1.5;color:#71717a;">%s</p>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>
          </body>
        </html>
        """.formatted(
        escape(subject),
        escape(logoUrl),
        escape(heading),
        escape(lead),
        escape(code),
        escape(expiry),
        escape(actionUrl),
        escape(actionLabel),
        escape(securityNotice),
        escape(ignoreNotice),
        escape(AUTOMATED_MESSAGE));

    return new Rendered(subject, text, html);
  }

  private String escape(String value) {
    return value == null ? "" : value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  record Rendered(String subject, String text, String html) {
  }
}
