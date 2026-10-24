// Política del entorno (AC-12 a AC-15 y sección 4b de PROJECT.md).
// Se ejecuta con scripts/tests/run-env-tests.sh dentro de un contenedor Node: sin dependencias.
// Servicios esperados: frontend, backend, db, db-test, ollama, mailpit.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const REPO = process.env.REPO_DIR ?? '/repo';
const load = (name) => JSON.parse(readFileSync(`${process.env.COMPOSE_OUT ?? '/out'}/${name}.json`, 'utf8'));
const read = (path) => readFileSync(`${REPO}/${path}`, 'utf8');

const base = load('base'); // compose.yaml con el perfil prod
const dev = load('dev'); // compose.yaml + compose.dev.yaml con el perfil dev
const testEnv = load('test'); // compose.yaml con el perfil test
const bench = load('benchmark'); // compose.yaml con el perfil benchmark (F02)
const all = [base, dev, testEnv, bench];
const services = (cfg) => Object.entries(cfg.services ?? {});
const published = (cfg) =>
  services(cfg).flatMap(([name, s]) => (s.ports ?? []).map((p) => ({ name, ...p })));

// ---------- AC-12: servicios ----------

test('AC-12 el entorno define frontend, backend, db, ollama y, en test, db-test', () => {
  for (const name of ['frontend', 'backend', 'db', 'ollama']) {
    assert.ok(base.services?.[name], `falta el servicio ${name} en prod`);
  }
  assert.ok(testEnv.services?.['db-test'], 'falta db-test en el perfil test');
  assert.ok(dev.services?.mailpit, 'falta mailpit en el perfil dev');
});

test('AC-12 todos los servicios tienen healthcheck', () => {
  for (const cfg of all) {
    for (const [name, s] of services(cfg)) {
      // Los contenedores de un solo uso (ejecutan los tests y terminan) se marcan con esta etiqueta
      if (s.labels?.['studylab.runner'] === 'true') continue;
      assert.ok(s.healthcheck?.test, `${name} no tiene healthcheck`);
    }
  }
});

test('AC-12 el arranque espera a que las dependencias estén sanas', () => {
  assert.equal(base.services.backend.depends_on?.db?.condition, 'service_healthy');
  assert.equal(base.services.frontend.depends_on?.backend?.condition, 'service_healthy');
});

test('AC-12 la BD usa la imagen con pgvector', () => {
  assert.match(base.services.db.image, /pgvector/);
});

// ---------- AC-13: perfiles y variables ----------

test('AC-13 existen los perfiles dev, test y prod y hay .env.example', () => {
  assert.ok(existsSync(`${REPO}/.env.example`), 'falta .env.example');
  assert.ok(existsSync(`${REPO}/compose.dev.yaml`), 'falta compose.dev.yaml (puertos solo de desarrollo)');
  assert.ok(testEnv.services['db-test'].profiles?.includes('test'));
});

test('AC-13 .env.example documenta las variables obligatorias sin valores reales', () => {
  const lines = read('.env.example').split('\n').filter((l) => /^[A-Z_]+=/.test(l));
  const env = Object.fromEntries(lines.map((l) => [l.split('=')[0], l.slice(l.indexOf('=') + 1).trim()]));
  for (const key of [
    'POSTGRES_PASSWORD', 'SPRING_DATASOURCE_PASSWORD', 'APP_PUBLIC_URL', 'MAIL_ENABLED', 'MAIL_FROM',
    'SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD',
  ]) {
    assert.ok(key in env, `.env.example no documenta ${key}`);
  }
  for (const [key, value] of Object.entries(env)) {
    if (/PASSWORD|SECRET|TOKEN/.test(key)) {
      assert.match(value, /^(|change[-_ ]?me|x+|<.*>)$/i, `${key} parece un secreto real en .env.example`);
    }
  }
});

test('AC-11 los secretos de Compose son obligatorios: ${VAR:?mensaje}, nunca un valor por defecto', () => {
  for (const cfg of all) {
    for (const [name, s] of services(cfg)) {
      for (const [key, value] of Object.entries(s.environment ?? {})) {
        // Único literal permitido: la credencial pública de Mailpit en dev (no valida usuarios)
        const mailpitDevCredential = name === 'backend' && key === 'SPRING_MAIL_PASSWORD' && value === 'mailpit';
        if (/PASSWORD|SECRET|TOKEN/.test(key) && !mailpitDevCredential) {
          assert.match(String(value), /^\$\{[A-Z_]+:\?.+\}$/, `${name}.${key} debe exigirse con \${VAR:?...}`);
        }
      }
    }
  }
});

test('AC-13 .env y claves están en .gitignore', () => {
  const ignore = read('.gitignore');
  assert.match(ignore, /^\.env$/m);
});

// ---------- AC-14: scripts estándar ----------

test('AC-14 el Makefile ofrece los scripts estándar', () => {
  const makefile = read('Makefile');
  for (const target of ['up', 'down', 'logs', 'health', 'test-backend', 'test-frontend', 'test-env', 'seed', 'reset-dev-db']) {
    assert.match(makefile, new RegExp(`^${target}:`, 'm'), `falta el objetivo ${target}`);
  }
});

test('AC-14 los tests se ejecutan en contenedor, sin docker.sock', () => {
  const makefile = read('Makefile');
  // El Makefile usa variables ($(TEST), $(DEV)...) que expanden a "docker compose ..."
  const compose = /(docker compose|\$\((COMPOSE|DEV|TEST)\))/.source;
  assert.match(makefile, new RegExp(`${compose}[^\\n]*(run|exec)[^\\n]*backend-test`));
  assert.match(makefile, new RegExp(`${compose}[^\\n]*(run|exec)[^\\n]*frontend-test`));
});

// ---------- AC-15: seguridad del entorno ----------

test('AC-15 ningún servicio es privileged, usa la red/pid del host ni monta docker.sock', () => {
  for (const cfg of all) {
    for (const [name, s] of services(cfg)) {
      assert.notEqual(s.privileged, true, `${name} es privileged`);
      assert.notEqual(s.network_mode, 'host', `${name} usa la red del host`);
      assert.notEqual(s.pid, 'host', `${name} usa el pid del host`);
      for (const v of s.volumes ?? []) {
        assert.doesNotMatch(String(v.source ?? ''), /docker\.sock/, `${name} monta docker.sock`);
      }
    }
  }
  assert.doesNotMatch(read('compose.yaml'), /docker\.sock/);
});

test('AC-15 todos los puertos publicados escuchan solo en 127.0.0.1', () => {
  for (const cfg of all) {
    for (const p of published(cfg)) {
      assert.equal(p.host_ip, '127.0.0.1', `${p.name} publica ${p.published} fuera de 127.0.0.1`);
    }
  }
});

test('AC-15 en producción solo el frontend publica puertos', () => {
  const names = new Set(published(base).map((p) => p.name));
  assert.deepEqual([...names], ['frontend']);
});

test('AC-15 backend, BD y ollama no están publicados fuera de dev', () => {
  for (const cfg of [base, testEnv]) {
    const names = new Set(published(cfg).map((p) => p.name));
    for (const internal of ['backend', 'db', 'db-test', 'ollama']) {
      assert.ok(!names.has(internal), `${internal} publica puertos fuera de dev`);
    }
  }
});

test('AC-15 los datos viven en volúmenes con nombre (Postgres y modelos de Ollama)', () => {
  for (const name of ['db', 'ollama']) {
    const mounts = base.services[name].volumes ?? [];
    assert.ok(mounts.some((v) => v.type === 'volume' && v.source), `${name} sin volumen con nombre`);
  }
});

test('AC-15 los procesos de backend y frontend no corren como root', () => {
  for (const dir of ['backend', 'frontend']) {
    const users = [...read(`${dir}/Dockerfile`).matchAll(/^USER\s+(\S+)/gm)].map((m) => m[1]);
    assert.ok(users.length > 0, `${dir}/Dockerfile no declara USER`);
    assert.doesNotMatch(users.at(-1), /^(root|0)$/, `${dir} termina como root`);
  }
});

test('AC-15 las imágenes no copian .env ni secretos', () => {
  for (const dir of ['backend', 'frontend']) {
    assert.doesNotMatch(read(`${dir}/Dockerfile`), /COPY[^\n]*\.env(?!\.example)/);
  }
  assert.ok(existsSync(`${REPO}/backend/.dockerignore`) && existsSync(`${REPO}/frontend/.dockerignore`));
  assert.match(read('backend/.dockerignore'), /^\.env/m);
});

test('AC-15 nginx solo reenvía /api y no expone actuator', () => {
  const conf = read('frontend/nginx.conf');
  assert.match(conf, /location\s+\/api\//);
  assert.doesNotMatch(conf, /actuator/);
});

test('AC-16 los logs van a stdout (sin ficheros de log dentro de la imagen)', () => {
  const props = existsSync(`${REPO}/backend/src/main/resources/application.properties`)
    ? read('backend/src/main/resources/application.properties')
    : '';
  assert.doesNotMatch(props, /logging\.file/);
});

// ---------- F02: capa de IA ----------

test('F02 el benchmark es un contenedor de un solo uso, sin puertos y detrás de su propio perfil', () => {
  const b = bench.services?.benchmark;
  assert.ok(b, 'falta el servicio benchmark');
  assert.deepEqual(b.profiles, ['benchmark']);
  assert.equal(b.labels?.['studylab.runner'], 'true');
  assert.equal(b.ports, undefined, 'el benchmark no publica puertos');
  assert.equal(b.depends_on?.db?.condition, 'service_healthy');
  assert.match(String(b.environment?.SPRING_PROFILES_ACTIVE), /benchmark/);
  // `docker compose config` lista todos los servicios con sus perfiles: que solo lleve el suyo basta para que `make up` no lo arranque
  for (const [name, s] of services(base)) {
    if (name !== 'benchmark') assert.ok(!(s.profiles ?? []).includes('benchmark'), `${name} no debe estar en el perfil benchmark`);
  }
});

test('F02 las claves de los proveedores son opcionales y nunca llevan un valor escrito en Compose', () => {
  for (const cfg of all) {
    for (const [name, s] of services(cfg)) {
      for (const [key, value] of Object.entries(s.environment ?? {})) {
        if (/_API_KEY$/.test(key)) {
          assert.match(String(value), /^\$\{[A-Z_]+:-\}$/, `${name}.${key} debe ser \${VAR:-} (vacío si no se usa)`);
        }
      }
    }
  }
  const keys = Object.keys(base.services.backend.environment);
  for (const k of ['ANTHROPIC_API_KEY', 'OPENAI_API_KEY', 'GEMINI_API_KEY']) assert.ok(keys.includes(k), `backend sin ${k}`);
});

test('F02 Ollama se alcanza por la red interna por defecto (la misma imagen sirve en producción)', () => {
  assert.match(String(base.services.backend.environment.OLLAMA_BASE_URL), /^\$\{OLLAMA_BASE_URL:-http:\/\/ollama:11434\}$/);
});

test('F02 .env.example documenta las variables de IA sin claves reales', () => {
  const lines = read('.env.example').split('\n').filter((l) => /^[A-Z_]+=/.test(l));
  const env = Object.fromEntries(lines.map((l) => [l.split('=')[0], l.slice(l.indexOf('=') + 1).trim()]));
  for (const key of ['ANTHROPIC_API_KEY', 'OPENAI_API_KEY', 'GEMINI_API_KEY', 'OLLAMA_BASE_URL', 'AI_MONTHLY_LIMIT_USD', 'AI_BUDGET_MODE']) {
    assert.ok(key in env, `.env.example no documenta ${key}`);
  }
  for (const key of ['ANTHROPIC_API_KEY', 'OPENAI_API_KEY', 'GEMINI_API_KEY']) assert.equal(env[key], '', `${key} debe ir vacía`);
  assert.match(env.AI_BUDGET_MODE, /^(WARN|BLOCK)$/);
});

test('F02 el Makefile ofrece benchmark y la descarga del modelo de embeddings', () => {
  const makefile = read('Makefile');
  for (const target of ['benchmark', 'pull-embedding-model']) {
    assert.match(makefile, new RegExp(`^${target}:`, 'm'), `falta el objetivo ${target}`);
  }
});

test('F02 el informe del benchmark se escribe en sdd/benchmarks y esa carpeta existe en el repositorio', () => {
  // En Windows la ruta del volumen lleva barras invertidas (que JSON.stringify duplica)
  assert.match(JSON.stringify(bench.services.benchmark.volumes ?? []), /sdd[\\/]+benchmarks/);
  assert.ok(existsSync(`${REPO}/sdd/benchmarks`), 'falta sdd/benchmarks');
});
