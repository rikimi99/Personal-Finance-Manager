# Personal Finance Manager

## Run locally

Requires Java 25. From this directory, start the app with:

```sh
./mvnw spring-boot:run
```

Open [http://localhost:8081](http://localhost:8081), create an account, and sign in. Passwords must be at least 12 characters. They are stored as BCrypt hashes; transaction endpoints use the signed-in account and do not accept a client-selected user ID.

Run the test suite with `./mvnw test`.

## Sessions and deployment

Authentication uses server-side sessions and CSRF protection. The browser UI requests a CSRF token and sends it with state-changing requests. Sessions survive page refreshes and end when the user signs out, the session times out, or the browser is closed (the session cookie is not persistent). Keep the frontend and API on the same origin. When serving the app over HTTPS, set `SESSION_COOKIE_SECURE=true`; leave it false only for local HTTP development.

Back up the SQLite database before deploying or changing its schema. Transactions from the former demo-user setup are not automatically transferred to new accounts. Migrate any existing personal data deliberately before using those records with a new account.

Set `SHOW_SQL=true` to log the SQL statements Hibernate runs (off by default). The SQLite database file (`pfm.db`) is created in the working directory on first start and is git-ignored, so it is never committed.
