# Stempel

**E-Rechnungen. Geprüft. Fertig.**

Stempel is a self-hosted web app for German small businesses to view and validate e-invoices (XRechnung and ZUGFeRD, based on EN 16931). It runs on your own machine with Docker, needs no account and stores nothing.

Work in progress. Version 1 will include the viewer and the validator.

## Run it

You need [Docker](https://www.docker.com/products/docker-desktop/). Then:

```sh
docker compose up --build
```

Open http://localhost:8080 and drop in an XRechnung (XML) or ZUGFeRD (PDF) file.

## License

[Apache 2.0](LICENSE)
