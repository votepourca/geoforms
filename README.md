# geoforms
Local Democracy Forms and Tools based on ClojureScript Reagent (react.js) and Matchbox (firebase) app for local democracy

This is Alpha software.


## What's inside

This is mainly a ClojureScript application that includes:
* [Reagent](https://github.com/reagent-project/reagent) - ClojureScript interface to Facebook's React
* [reagent-forms](https://github.com/reagent-project/reagent-forms) - data binding library for Reagent
* [Matchbox](https://github.com/crisptrutski/matchbox) - a Firebase client for Clojure(Script)

It also has a clojure backend for now (but might be removed) and Heroku facilities for deployment.


## Usage

### Development mode

To run the Figwheel development server, run:

```
lein do clean, run
```

The application will now be available at [http://localhost:3000](http://localhost:3000).

To start the Figwheel compiler, run the following command in a separate terminal:

```
lein figwheel
```
Figwheel will automatically push cljs changes to the browser.

If you're only doing client-side development then it's sufficient to simply run the
Figwheel compiler and then browse to [http://localhost:3449](http://localhost:3449)
once it starts up.


## Contributors

Many thanks to Chris Truter (crisptrutski) for his contribution.


## License

Copyright © 2015 Votepour.ca and Leon Talbot

Distributed under the The MIT License (MIT).



