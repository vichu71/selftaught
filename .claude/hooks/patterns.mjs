// Patrones de secretos compartidos por los hooks de seguridad.
export const SECRET_CONTENT = [
  /sk-ant-[A-Za-z0-9_-]{20,}/, // Anthropic
  /sk-[A-Za-z0-9_-]{32,}/, // OpenAI y similares
  /AKIA[0-9A-Z]{16}/, // AWS
  /AIza[0-9A-Za-z_-]{35}/, // Google
  /-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----/,
];

// .env, .env.local, claves y certificados; se permiten .env.example/.sample/.template
export const SECRET_FILE =
  /(^|\/)(\.env(\.(?!(?:example|sample|template)$)[^/]*)?|[^/]*\.(?:pem|key|p12|pfx)|id_(?:rsa|ed25519))$/i;

export function findSecret(text) {
  return SECRET_CONTENT.find((re) => re.test(text));
}
