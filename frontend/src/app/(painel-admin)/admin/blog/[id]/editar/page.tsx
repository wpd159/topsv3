import { BlogPostForm } from "../../components/blog-post-form"

export default async function EditarPostPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  return <BlogPostForm mode="edit" postId={id} />
}
