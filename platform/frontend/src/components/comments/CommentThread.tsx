"use client";

import { useState } from "react";
import { Comment } from "@/types";
import { CommentForm } from "./CommentForm";
import { formatRelativeTime } from "@/lib/utils";

interface CommentItemProps {
  comment: Comment;
  onReply: (body: string, parentId: string) => Promise<void>;
  depth?: number;
}

function CommentItem({ comment, onReply, depth = 0 }: CommentItemProps) {
  const [replying, setReplying] = useState(false);

  const authorName = comment.author?.name || comment.author?.email || "Unknown";

  return (
    <div className={depth > 0 ? "ml-8 border-l-2 border-gray-100 pl-4" : ""}>
      <div className="py-3">
        <div className="flex items-center gap-2 mb-1">
          <div className="h-6 w-6 rounded-full bg-indigo-100 flex items-center justify-center text-xs font-medium text-indigo-700">
            {authorName[0]?.toUpperCase()}
          </div>
          <span className="text-sm font-medium text-gray-900">{authorName}</span>
          <span className="text-xs text-gray-400">{formatRelativeTime(comment.createdAt)}</span>
        </div>
        <p className="text-sm text-gray-700 whitespace-pre-wrap">{comment.body}</p>
        {depth === 0 && (
          <button
            onClick={() => setReplying(!replying)}
            className="mt-1 text-xs text-gray-400 hover:text-gray-600"
          >
            Reply
          </button>
        )}
      </div>

      {replying && (
        <div className="ml-8 mb-3">
          <CommentForm
            parentId={comment.id}
            placeholder="Write a reply..."
            onSubmit={async (body, parentId) => {
              await onReply(body, parentId!);
              setReplying(false);
            }}
            onCancel={() => setReplying(false)}
          />
        </div>
      )}

      {comment.replies?.map((reply) => (
        <CommentItem key={reply.id} comment={reply} onReply={onReply} depth={depth + 1} />
      ))}
    </div>
  );
}

interface CommentThreadProps {
  comments: Comment[];
  onAddComment: (body: string, parentId?: string) => Promise<void>;
}

export function CommentThread({ comments, onAddComment }: CommentThreadProps) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-gray-700 mb-3">
        Comments ({comments.length})
      </h3>

      <div className="divide-y divide-gray-100">
        {comments.map((c) => (
          <CommentItem key={c.id} comment={c} onReply={onAddComment} />
        ))}
      </div>

      <div className="mt-4">
        <CommentForm onSubmit={onAddComment} placeholder="Add a comment..." />
      </div>
    </div>
  );
}
