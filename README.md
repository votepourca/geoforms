# geoforms
Reagent app for local democracy tools

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

#### Optional development tools

Start the browser REPL:

```
$ lein repl
```
The Jetty server can be started by running:

```clojure
(start-server)
```
and stopped by running:
```clojure
(stop-server)
```

### Building for release

```
lein cljsbuild clean
lein uberjar
```

## Contents

The template packages everything you need to create a production ready ClojureScript application following current best practices. The template uses the following features and libraries:

* [Reagent](https://github.com/reagent-project/reagent) - ClojureScript interface to Facebook's React
* [reagent-forms](https://github.com/reagent-project/reagent-forms) - data binding library for Reagent
* [reagent-utils](https://github.com/reagent-project/reagent-utils) - utilities such as session and cookie management
* [Secretary](https://github.com/gf3/secretary) - client-side routing
* [Hiccup](https://github.com/weavejester/hiccup) - server-side HTML templating
* [Compojure](https://github.com/weavejester/compojure) - a popular routing library
* [Ring](https://github.com/ring-clojure/ring) - Clojure HTTP interface
* [Prone](https://github.com/magnars/prone) - better exception reporting middleware for Ring
* [Heroku](https://www.heroku.com/) - the template is setup to work on Heroku out of the box, simply run `git push heroku master`
* [clojurescript.test](https://github.com/cemerick/clojurescript.test) - a maximal port of clojure.test to ClojureScript
 
## License

Copyright © 2015 Votepour.ca and Leon Talbot

Distributed under the The MIT License (MIT).



