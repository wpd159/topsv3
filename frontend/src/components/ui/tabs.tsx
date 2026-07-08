"use client"

import * as React from "react"
import * as TabsPrimitive from "@radix-ui/react-tabs"
import { cn } from "@/lib/utils"

function Tabs({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Root>) {
  return (
    <TabsPrimitive.Root
      data-slot="tabs"
      className={cn("flex flex-col w-full", className)}
      {...props}
    />
  )
}

function TabsList({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      data-slot="tabs-list"
      className={cn(
        "flex w-full border-b border-gray-200 dark:border-gray-800", // linha até o fim do card
        className
      )}
      {...props}
    />
  )
}

function TabsTrigger({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      data-slot="tabs-trigger"
      className={cn(
        "relative -mb-px flex-1 cursor-pointer items-center justify-center " +
          "px-4 py-3 text-base font-medium text-gray-600 dark:text-gray-400 " +
          "hover:text-gray-900 dark:hover:text-gray-100 " +
          "data-[state=active]:text-gray-900 dark:data-[state=active]:text-white " +
          "data-[state=active]:after:absolute data-[state=active]:after:bottom-0 " +
          "data-[state=active]:after:left-0 data-[state=active]:after:h-[3px] " +
          "data-[state=active]:after:w-full data-[state=active]:after:bg-[#FC1EAD] " +
          "transition-all duration-200 ease-out",
        className
      )}
      {...props}
    />
  )
}

function TabsContent({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return (
    <TabsPrimitive.Content
      data-slot="tabs-content"
      className={cn("mt-4 flex-1 outline-none", className)}
      {...props}
    />
  )
}

export { Tabs, TabsList, TabsTrigger, TabsContent }
