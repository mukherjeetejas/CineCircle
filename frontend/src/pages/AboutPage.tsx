import { Link } from 'react-router-dom'
import { Logo } from '../components/Logo'
import { TMDB_NOTICE, TmdbLogo } from '../components/TmdbAttribution'

export function AboutPage() {
  return (
    <div className="space-y-4">
      <section className="card p-6">
        <Logo asLink={false} className="text-3xl" />
        <h1 className="mt-4 text-2xl font-bold">About cinecircle</h1>
        <p className="mt-2 leading-relaxed text-ink/85">
          A place to post movie reviews, follow your friends and upvote the hottest takes. Every review is tied to a real
          movie from The Movie Database, so there's no guessing which “Dune” someone meant.
        </p>
        <div className="mt-4 flex gap-2">
          <Link to="/popular" className="btn btn-primary">
            See what's popular
          </Link>
          <Link to="/movies/search" className="btn btn-outline">
            Find a movie
          </Link>
        </div>
      </section>

      <section className="card p-6" aria-labelledby="attribution-heading">
        <h2 id="attribution-heading" className="text-lg font-bold">
          Movie data
        </h2>
        <a
          href="https://www.themoviedb.org"
          target="_blank"
          rel="noreferrer"
          className="mt-4 inline-block rounded-lg bg-surface-2 p-4"
          aria-label="The Movie Database (opens in a new tab)"
        >
          <TmdbLogo className="h-6 w-auto" />
        </a>
        <p className="mt-4 text-sm text-ink">{TMDB_NOTICE}</p>
        <p className="mt-2 text-sm text-muted">
          Titles, posters, backdrops, overviews and TMDB scores come from TMDB. Reviews, ratings and the CineCircle score
          come from our users.
        </p>
      </section>
    </div>
  )
}
