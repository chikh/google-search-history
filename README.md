# Simple HTTP google search service

## Overview
This is simple (but theoretically scalable) HTTP service which provides links from Google's search
first page and stores search history.

## Usage
1. Install Java (JDK) version 7 at least
1. Install SBT
1. Clone this repository
1. (Optional) Adjust `src/main/resources/application.conf` file according your needs
1. Run `sbt run` command from your repository's root directory
1. Go to `localhost:8080/search/<your-search-word>` to get search result links (host and port might
be different according to your settings)
1. Go to `localhost:8080/history` to get search history (host and port might be different
according to your settings)

## Implementation details and limitations
Leverages Akka framework and consists of actors baked in a functional way. There is a requirement
to have only two actors (one for the history logging and one for the requesting), but Google's
limitations of non-browser search queries made querying actor's implementation way too convoluted.
So there is a need to split it into two actors that could potentially be easily scale to increase
throughput of service.

Also:
1. There is possible to search only for single keyword (not a sentence) at once because query
escaping is not implemented
1. Page parser gets all URLs from the search result page not only the actual search results
1. Failure domains are not fully elaborated
