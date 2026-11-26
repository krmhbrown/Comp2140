# Peak Finds: shared foundation

Java 17+, a built-in web server (`com.sun.net.httpserver`), SQLite through JDBC, plain HTML/CSS/JS. The only
third-party library is the `sqlite-jdbc` driver.
Currently run using Maven

## Run it
Download Apache Maven and follow the installation instructions, then type the following command into project terminal

mvn compile exec:java                 # then open http://localhost:8080
mvn test-compile exec:java@tests      # run the foundation tests


Test logins (password for all: `Password123!`):
`owner@peakfinds.test`,- OWNER
`customer1@example.test`,- CUSTOMER 1
`customer2@example.test`,- CUSTOMER 2 
`bearer1@peakfinds.test`, - BEARER 1
`bearer2@peakfinds.test`.- BEARER 2

To reset the data, stop the app and delete the `data/` folder; the next start rebuilds it from `schema.sql` and `seed.sql`.

Settings come from environment variables: `PF_PORT` (8080), `PF_DB_PATH` (data/peakfinds.db),
`PF_SECURE_COOKIES` (set `true` once served over HTTPS), `PF_SMTP_HOST`, `PF_SMTP_PORT`, `PF_SMTP_USER`,
`PF_SMTP_PASSWORD`, `PF_MAIL_FROM`. Never commit the email password.
