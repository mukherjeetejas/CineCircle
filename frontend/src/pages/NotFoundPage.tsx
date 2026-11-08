import { Link } from 'react-router-dom'
import { EmptyState } from '../components/EmptyState'

export function NotFoundPage() {
  return (
    <EmptyState
      title="404: Scene missing"
      description="This page ended up on the cutting room floor."
      action={
        <Link to="/" className="btn btn-primary">
          Take me home
        </Link>
      }
    />
  )
}
