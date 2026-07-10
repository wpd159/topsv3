import * as React from "react"
import { Slot } from "@radix-ui/react-slot"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

const buttonVariants = cva(
  "inline-flex items-center rounded-3xl justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-all disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4 shrink-0 [&_svg]:shrink-0 outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive",
  {
    variants: {
      variant: {
        default: "bg-[#FC1EAD] rounded-3xl cursor-pointer text-primary-foreground hover:text-none hover:bg-[#FC1EAD]/90",
        destructive:
          "bg-destructive cursor-pointer rounded-2xl text-white hover:bg-destructive/90 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 dark:bg-destructive/60",
        outline:
          "border rounded-2xl cursor-pointer bg-background hover:bg-accent hover:text-none dark:bg-input/30 dark:border-input dark:hover:bg-input/50",
        secondary:
          "bg-secondary cursor-pointer rounded-2xl text-secondary-foreground hover:bg-secondary/80",
        ghost:
          "hover:bg-accent cursor-pointer rounded-2xl hover:text-none dark:hover:bg-accent/50",
        link: "text-primary cursor-pointer rounded-2xl underline-offset-4 hover:underline",
      },
      size: {
        default: "h-9 px-4 py-3 has-[>svg]:px-3",
        sm: "h-8 rounded-md gap-1.5 px-3 has-[>svg]:px-2.5",
        lg: "h-10 rounded-md px-6 has-[>svg]:px-4",
        icon: "size-9",
        "icon-sm": "size-8",
        "icon-lg": "size-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant,
  size,
  asChild = false,
  onKeyDown,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot : "button"

  const handleKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.ctrlKey && !event.altKey && !event.metaKey) {
      event.preventDefault()
      event.currentTarget.click()
    }
    onKeyDown?.(event)
  }

  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      onKeyDown={handleKeyDown}
      {...props}
    />
  )
}

export { Button, buttonVariants }
