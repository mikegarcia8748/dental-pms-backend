# dental-pms

Practice management system for a dental clinic — patient records, dental chart,
visits, procedures, billing and payments. See [docs/](docs/) for the functional
specification, starting with [project-overview.md](docs/project-overview.md).

## Stack

| Layer          | Choice                                              |
|----------------|-----------------------------------------------------|
| Language       | Kotlin (JVM 21), built with Amper                   |
| Server         | Ktor (Netty)                                        |
| Database       | Neon serverless PostgreSQL                          |
| Data access    | Exposed + HikariCP                                  |
| Migrations     | Flyway                                              |
| Auth           | Self-hosted email + password, JWT access/refresh    |
| DI             | Koin                                                |
| Observability  | Call logging + Micrometer/Prometheus                |

## Building & Running

Both paths run the same code and read the same configuration, so day-to-day
development needs no container rebuild.

### Locally (fast edit/run loop)

| Task                                | Description                    |
|-------------------------------------|--------------------------------|
| `./kotlin test`                     | Run the tests                  |
| `./kotlin build`                    | Build the project              |
| `./kotlin run`                      | Run the server                 |
| `./kotlin package -f executable-jar`| Build a self-contained fat jar |

On Windows use `.\kotlin.bat` instead of `./kotlin`.

### In Docker

```bash
cp .env.example .env        # fill in real values
docker compose up --build   # http://localhost:8080/health
```

`docker compose --profile local-db up` additionally starts a local PostgreSQL
instead of Neon. `docker compose build` alone produces the `dental-pms:latest`
image if you prefer plain `docker run --env-file .env -p 8080:8080 dental-pms`.

The image is built in two stages: the Amper wrapper produces an executable jar,
which is then copied onto a JRE base and run as a non-root user.

Once running, the base URL `/` returns a small JSON service index (links to `/health`,
`/swagger`, and `/api.json`), and `GET /health` returns `{"status":"ok"}`. Note that
any other path — including a bare `/does-not-exist` — returns a JSON `404`, not a page.

## Configuration

Configuration comes from environment variables (see `.env.example`). Real
environment variables always win; anything they leave unset is read from a `.env`
file in the working directory, which is what makes `./kotlin run` convenient.
Containers receive real environment variables — via compose's `env_file` or
`docker run --env-file` — and never contain the file. Nothing secret is
committed; `.env` is gitignored.

### Access from other machines on the LAN

The server binds `SERVER_HOST` (default `0.0.0.0`) on `PORT` (default `8080`), so
it is reachable at `http://<your-lan-ip>:8080` in both modes — compose publishes
the port on every host interface. Set `SERVER_HOST=127.0.0.1` locally, or the mapping to
`"127.0.0.1:8080:8080"` in `compose.yaml`, to restrict it to this machine.

The canonical port is `8080` everywhere. If it is already taken, don't scatter a second
number across the stack: for a local `./kotlin run`, set `PORT` (e.g. `PORT=8081`) to move
just that process; for the compose container, set `HOST_PORT` to move the *published* port
while the container keeps listening on 8080. Swagger's "Try it out" follows the origin it
was loaded from, so it works on whichever port you land on.

On Windows, the first local run may need a firewall rule allowing inbound
connections to the JVM on that port; Docker Desktop adds its own rule for
published ports.
