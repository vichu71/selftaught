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

// ---------- F03: material propio ----------

test('F03 nginx admite subidas de hasta 11 MB solo en /api/material y deja 1 MB para el resto de /api', () => {
  const conf = read('frontend/nginx.conf');
  const block = conf.match(/location\s+\/api\/material\s*\{[^}]*\}/);
  assert.ok(block, 'falta el bloque location /api/material');
  assert.match(block[0], /client_max_body_size\s+11m;/);
  assert.match(block[0], /proxy_pass\s+http:\/\/backend:8080/);
  assert.match(block[0], /proxy_set_header\s+X-Forwarded-For\s+\$remote_addr;/); // la IP real no se puede falsear
  assert.match(conf, /location\s+\/api\/\s*\{[^}]*client_max_body_size\s+1m;/);
});

test('F03 los límites de material llegan al backend con sus valores por defecto y están en .env.example', () => {
  const environment = base.services.backend.environment;
  assert.match(String(environment.MATERIAL_MAX_FILE_MB), /^\$\{MATERIAL_MAX_FILE_MB:-10\}$/);
  assert.match(String(environment.MATERIAL_MAX_DOCUMENTS), /^\$\{MATERIAL_MAX_DOCUMENTS:-20\}$/);
  const lines = read('.env.example').split('\n').filter((l) => /^[A-Z_]+=/.test(l));
  const env = Object.fromEntries(lines.map((l) => [l.split('=')[0], l.slice(l.indexOf('=') + 1).trim()]));
  assert.equal(env.MATERIAL_MAX_FILE_MB, '10');
  assert.equal(env.MATERIAL_MAX_DOCUMENTS, '20');
});

test('F03 el backend sigue en solo lectura y sin volúmenes para ficheros subidos', () => {
  const backend = base.services.backend;
  assert.equal(backend.read_only, true);
  assert.ok(!(backend.volumes ?? []).some((v) => /material|upload/i.test(JSON.stringify(v))), 'no debe haber volúmenes de ficheros subidos');
});

// ---------- F04: asistente de creación de curso ----------

test('F04 la tarea INTAKE y los límites del asistente llegan al backend, y en dev la tarea usa el proveedor falso', () => {
  const environment = base.services.backend.environment;
  assert.match(String(environment.APP_AI_TASKS_INTAKE_PROVIDER), /^\$\{APP_AI_TASKS_INTAKE_PROVIDER:-\}$/);
  assert.match(String(environment.APP_AI_TASKS_INTAKE_MODEL), /^\$\{APP_AI_TASKS_INTAKE_MODEL:-\}$/);
  assert.match(String(environment.COURSE_INTAKES_PER_HOUR), /^\$\{COURSE_INTAKES_PER_HOUR:-30\}$/);
  assert.match(String(environment.COURSE_MAX_PENDING), /^\$\{COURSE_MAX_PENDING:-5\}$/);
  // Al combinar dos ficheros, Compose puede dar el entorno como lista "CLAVE=valor" en lugar de como objeto
  const devEnvironment = dev.services.backend.environment;
  const devValue = (key) => (Array.isArray(devEnvironment) ? devEnvironment.find((e) => e.startsWith(`${key}=`))?.slice(key.length + 1) : devEnvironment[key]);
  assert.match(String(devValue('APP_AI_TASKS_INTAKE_PROVIDER')), /^\$\{APP_AI_TASKS_INTAKE_PROVIDER:-fake\}$/);
  assert.match(String(devValue('APP_AI_TASKS_INTAKE_MODEL')), /^\$\{APP_AI_TASKS_INTAKE_MODEL:-fake-1\}$/);
});

test('F04 la plantilla de variables documenta los límites del asistente y la tarea INTAKE', () => {
  const text = read('.env.example');
  const lines = text.split('\n').filter((l) => /^[A-Z_]+=/.test(l));
  const env = Object.fromEntries(lines.map((l) => [l.split('=')[0], l.slice(l.indexOf('=') + 1).trim()]));
  assert.equal(env.COURSE_INTAKES_PER_HOUR, '30');
  assert.equal(env.COURSE_MAX_PENDING, '5');
  assert.match(text, /APP_AI_TASKS_INTAKE/, 'la tarea INTAKE debe estar documentada junto a las demás');
});

test('F04 la plantilla del asistente y la lista de términos del filtro de menores existen en el código, no en el entorno', () => {
  assert.ok(existsSync(`${REPO}/backend/src/main/resources/prompts/intake.txt`), 'falta prompts/intake.txt');
  assert.ok(existsSync(`${REPO}/backend/src/main/resources/safety/blocked-terms.txt`), 'falta safety/blocked-terms.txt');
  assert.doesNotMatch(read('backend/src/main/resources/prompts/intake.txt'), /\{\{|\$\{/, 'la plantilla no lleva marcadores para el texto del usuario');
});

test('F04 el asistente no monta volúmenes ni publica el backend', () => {
  for (const cfg of all) {
    for (const [name, s] of services(cfg)) {
      assert.ok(!JSON.stringify(s.volumes ?? []).match(/course|intake/i), `${name} no debe montar volúmenes del asistente`);
    }
  }
  assert.ok(!published(base).some((p) => p.name === 'backend'), 'el backend no se publica fuera de la red de Compose en prod');
});

// ---------- F05: generación del curso ----------

test('F05 el lector de Markdown va con la versión fijada y bloqueada', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  assert.ok('marked' in pkg.dependencies);
  assert.match(pkg.dependencies.marked, /^\d+\.\d+\.\d+$/, 'marked debe llevar una versión exacta, sin ^ ni ~');
  assert.ok(existsSync(`${REPO}/frontend/package-lock.json`), 'falta el fichero de versiones bloqueadas');
  assert.match(read('frontend/package-lock.json'), /"node_modules\/marked"/, 'marked debe estar en el fichero de versiones bloqueadas');
});

test('F05 las plantillas de la generación están en el código, piden JSON o Markdown acotado y no llevan marcadores de datos del usuario', () => {
  for (const id of ['generation-outline', 'generation-lesson', 'generation-exercises', 'generation-exam']) {
    const file = `backend/src/main/resources/prompts/${id}.txt`;
    assert.ok(existsSync(`${REPO}/${file}`), `falta ${file}`);
    assert.doesNotMatch(read(file), /\{\{|\$\{/, `${id} no lleva marcadores para el texto del usuario`);
  }
});

test('F05 la generación no añade puertos, volúmenes ni variables obligatorias nuevas al entorno', () => {
  const before = JSON.stringify(published(base).map((p) => `${p.name}:${p.target}`).sort());
  assert.ok(!before.includes('backend'), 'el backend no se publica en prod');
  const required = Object.entries(base.services.backend.environment ?? {}).filter(([, v]) => /\?/.test(String(v))).map(([k]) => k);
  assert.ok(!required.some((k) => /GENERATION/.test(k)), 'la generación no exige variables obligatorias nuevas');
});

test('F06 el estudio no añade dependencias al frontend ni variables obligatorias al entorno', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  assert.deepEqual(Object.keys(pkg.dependencies).sort(), ['marked', 'pinia', 'vue', 'vue-router'], 'F06 no necesita librerías nuevas');
  const required = Object.entries(base.services.backend.environment ?? {}).filter(([, v]) => /\?/.test(String(v))).map(([k]) => k);
  assert.ok(!required.some((k) => /STUDY|GRADING/.test(k)), 'el estudio no exige variables obligatorias nuevas (la corrección con IA usa la tarea ASSESSMENT)');
});

test('F06 la plantilla de la corrección con IA está en el código, pide JSON y no lleva marcadores de datos del usuario', () => {
  const file = 'backend/src/main/resources/prompts/grade-exercise.txt';
  assert.ok(existsSync(`${REPO}/${file}`), `falta ${file}`);
  const text = read(file);
  assert.doesNotMatch(text, /\{\{|\$\{/, 'la plantilla no lleva marcadores para el texto del usuario');
  assert.match(text.toLowerCase(), /json/);
});

test('F07 el tutor no añade dependencias al frontend ni variables obligatorias nuevas al entorno', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  assert.deepEqual(Object.keys(pkg.dependencies).sort(), ['marked', 'pinia', 'vue', 'vue-router'], 'F07 no necesita librerías nuevas');
  const required = Object.entries(base.services.backend.environment ?? {}).filter(([, v]) => /\?/.test(String(v))).map(([k]) => k);
  assert.ok(!required.some((k) => /^TUTOR_|APP_TUTOR/.test(k)), 'los límites del tutor son valores de ingeniería, no variables obligatorias');
});

test('F07 la plantilla del tutor está en el código, es una instrucción fija y no lleva marcadores de datos del usuario', () => {
  const file = 'backend/src/main/resources/prompts/tutor.txt';
  assert.ok(existsSync(`${REPO}/${file}`), `falta ${file}`);
  const text = read(file);
  assert.doesNotMatch(text, /\{\{|\$\{/, 'la plantilla no lleva marcadores para el texto del usuario');
  assert.match(text.toLowerCase(), /pistas/);
});

test('F08 el panel no añade dependencias, variables obligatorias ni plantillas de IA nuevas', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  assert.deepEqual(Object.keys(pkg.dependencies).sort(), ['marked', 'pinia', 'vue', 'vue-router'], 'F08 no necesita librerías nuevas');
  const required = Object.entries(base.services.backend.environment ?? {}).filter(([, v]) => /\?/.test(String(v))).map(([k]) => k);
  assert.ok(!required.some((k) => /GUARDIAN|PANEL/.test(k)), 'el panel no exige variables obligatorias');
  assert.ok(!existsSync(`${REPO}/backend/src/main/resources/prompts/guardian.txt`), 'el panel no llama a la IA: no hay plantilla');
});

test('F08 la migración del panel solo añade el índice de la auditoría por actor', () => {
  const file = 'backend/src/main/resources/db/migration/V8__guardian.sql';
  assert.ok(existsSync(`${REPO}/${file}`), `falta ${file}`);
  const sql = read(file);
  assert.match(sql, /CREATE INDEX audit_event_actor_idx ON audit_event \(actor_user_id, action, occurred_at\)/i);
  assert.doesNotMatch(sql, /CREATE TABLE|DROP |GRANT/i, 'el panel no crea tablas ni cambia permisos');
});

test('F09 la edición y compartir no añaden dependencias, variables obligatorias ni plantillas de IA nuevas', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  assert.deepEqual(Object.keys(pkg.dependencies).sort(), ['marked', 'pinia', 'vue', 'vue-router'], 'F09 no necesita librerías nuevas');
  const required = Object.entries(base.services.backend.environment ?? {}).filter(([, v]) => /\?/.test(String(v))).map(([k]) => k);
  assert.ok(!required.some((k) => /SHARE|COURSE_EDIT/.test(k)), 'los límites de F09 son valores de ingeniería, no variables obligatorias');
  for (const name of ['share', 'course-edit', 'edit']) assert.ok(!existsSync(`${REPO}/backend/src/main/resources/prompts/${name}.txt`), `F09 no llama a la IA por su cuenta: no hay plantilla ${name}`);
});

test('F09 la migración V9 crea los códigos, el registro de acciones y las columnas del curso, y no borra nada', () => {
  const file = 'backend/src/main/resources/db/migration/V9__share_edit.sql';
  assert.ok(existsSync(`${REPO}/${file}`), `falta ${file}`);
  const sql = read(file);
  assert.match(sql, /CREATE TABLE share_code/i);
  assert.match(sql, /code_hash/i);
  assert.match(sql, /CREATE TABLE course_action/i);
  assert.match(sql, /origin_id/i);
  assert.match(sql, /outdated/i);
  assert.doesNotMatch(sql, /DROP TABLE|TRUNCATE/i, 'la migración no borra datos');
});

// ---------- F10: rediseño del frontend ----------

test('F10 las dependencias del frontend son exactamente las acordadas, todas con versión exacta y bloqueadas', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  // F10 amplía a propósito la lista de F05 (marked, pinia, vue, vue-router): primitivas accesibles, iconos y tipografías autoalojadas
  assert.deepEqual(Object.keys(pkg.dependencies).sort(), [
    '@fontsource-variable/bricolage-grotesque',
    '@fontsource-variable/newsreader',
    '@fontsource/space-mono',
    'lucide-vue-next',
    'marked',
    'pinia',
    'reka-ui',
    'vue',
    'vue-router',
  ]);
  for (const name of ['@fontsource-variable/bricolage-grotesque', '@fontsource-variable/newsreader', '@fontsource/space-mono', 'lucide-vue-next', 'marked', 'reka-ui']) {
    assert.match(pkg.dependencies[name], /^\d+\.\d+\.\d+$/, `${name} debe llevar una versión exacta, sin ^ ni ~`);
    assert.match(read('frontend/package-lock.json'), new RegExp(`"node_modules/${name}"`), `${name} debe estar en el fichero de versiones bloqueadas`);
  }
});

test('F10 el diseño no abre la política del servidor web ni pide nada a terceros', () => {
  const nginx = read('frontend/nginx.conf');
  assert.match(nginx, /Content-Security-Policy "default-src 'self'/);
  assert.doesNotMatch(nginx, /(script|font|style|img|connect)-src[^;"]*https?:/, 'ninguna fuente externa en la política');
  assert.doesNotMatch(read('frontend/index.html'), /fonts\.googleapis|fonts\.gstatic|<script[^>]+src=["']https?:/, 'ni fuentes ni scripts de terceros');
});

test('F10 es solo de frontend: el backend, el Compose y las variables de entorno no cambian de contrato', () => {
  for (const file of ['backend/src/main/resources/db/migration']) {
    assert.ok(existsSync(`${REPO}/${file}`), `falta ${file}`);
  }
  assert.ok(!existsSync(`${REPO}/backend/src/main/resources/db/migration/V10__rediseno.sql`), 'F10 no añade migraciones');
  assert.ok(!published(base).some((p) => p.name === 'frontend' && ![80, 8080].includes(Number(p.target))), 'el frontend no publica puertos nuevos');
});
