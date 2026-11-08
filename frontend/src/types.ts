// DTOs exactly as defined in the API contract (CLAUDE.md).

export interface Page<T> {
  items: T[]
  page: number
  size: number
  hasNext: boolean
}

export interface ApiErrorBody {
  status: number
  code: string
  message: string
  fieldErrors?: Record<string, string>
}

export type FollowStatus = 'NONE' | 'PENDING' | 'ACCEPTED' | 'SELF'

export interface UserSummary {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
  isPrivate: boolean
}

export interface UserProfile extends UserSummary {
  bio: string | null
  followerCount: number
  followingCount: number
  postCount: number
  createdAt: string
  followStatus: FollowStatus
  canViewPosts: boolean
}

export interface MovieSummary {
  tmdbId: number
  title: string
  releaseYear: number | null
  posterUrl: string | null
  tmdbVoteAverage: number | null
  reviewCount: number
  avgRating: number | null
}

export interface MovieDetail extends MovieSummary {
  overview: string | null
  backdropUrl: string | null
  genres: string[]
}

export interface PostMovie {
  tmdbId: number
  title: string
  releaseYear: number | null
  posterUrl: string | null
}

export type PostSource = 'FOLLOWING' | 'POPULAR'

export interface PostSummary {
  id: string
  author: UserSummary
  movie: PostMovie
  title: string
  bodyPreview: string
  rating: number
  containsSpoilers: boolean
  imageUrl: string | null
  upvoteCount: number
  commentCount: number
  upvotedByMe: boolean
  isMine: boolean
  createdAt: string
  source?: PostSource
}

export interface PostDetail extends PostSummary {
  body: string
}

export interface Comment {
  id: string
  author: UserSummary
  body: string
  deleted: boolean
  createdAt: string
  isMine: boolean
  replies: Comment[]
}

// ----- Requests / small responses -----

export interface RegisterRequest {
  username: string
  email: string
  password: string
  displayName: string
}

export interface LoginRequest {
  usernameOrEmail: string
  password: string
}

/** `POST /auth/register|login` → `{token, user: UserProfile}` */
export interface AuthResponse {
  token: string
  user: UserProfile
}

export interface UpdateMeRequest {
  displayName?: string
  bio?: string
  isPrivate?: boolean
}

export interface FollowResponse {
  followStatus: FollowStatus
}

export interface UpvoteResponse {
  upvoted: boolean
  upvoteCount: number
}

export type ImageType = 'NONE' | 'TMDB_POSTER' | 'CUSTOM'

export interface CreatePostRequest {
  tmdbId: number
  title: string
  body: string
  rating: number
  containsSpoilers: boolean
  imageType: ImageType
}

export interface CreateCommentRequest {
  body: string
  parentCommentId?: string
}

export type PopularWindow = 'day' | 'week' | 'all'
export type MoviePostSort = 'top' | 'new'
