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

```
lein do clean, run
```
or
```
FB_BASE="https://<your-app>.firebaseio.com/" lein do clean, run
```
Then
```
lein figwheel
```
or
```
FB_BASE="https://<your-app>.firebaseio.com/" lein figwheel
```

The application will now be available at [http://localhost:3000](http://localhost:3000).


### Static deployment (to javascript)

```
lein with-profile prod cljsbuild once
```
or
```
FB_BASE="https://<your-app>.firebaseio.com/" lein with-profile prod cljsbuild once
```

## Contributors

Many thanks to Chris Truter (crisptrutski) for his contribution.


## License

Copyright © 2015 Votepour.ca and Leon Talbot

Distributed under the The MIT License (MIT).



