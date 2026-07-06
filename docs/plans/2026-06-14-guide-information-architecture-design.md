# Guide Information Architecture Design

## Goal

Turn `/guide` from a dense feature encyclopedia into a mixed guide that supports both first-time learning and later reference, while preserving the existing paged topic URLs and screenshot content.

## Information Architecture

The page keeps three desktop columns with one responsibility per column:

- The left column is the global guide directory. Topics are grouped into "快速开始", "看学练测", and "成长与教学" so readers can understand the learning path before choosing a feature.
- The center column is the current topic. Every topic starts with its purpose and learning-loop role, then moves through scenarios, operation steps, capabilities, boundaries, and related modules.
- The right column is only the current-page outline. It no longer repeats links to every other topic.

The page header becomes a compact orientation block rather than a second article. It introduces the learning loop and offers direct entry points to the most important beginner topics.

## Sticky Navigation

On desktop, both the global directory and current-page outline remain sticky below the application header. Each column scrolls internally when its content exceeds the viewport.

On medium screens, the global directory remains sticky and the current-page outline moves into a compact block above the article. On mobile, both navigation levels are available through the existing expandable chapter navigation, with the current page's subsections shown under the active topic.

## AI Guide Content

The AI Guide page explains four distinct roles:

1. Global navigator: recommends the next learning action from progress and profile context.
2. Contextual tutor: answers against the current video, courseware page, or problem.
3. Problem-solving coach: supports staged guidance from reading the prompt through post-AC review without exposing a submit-ready answer.
4. Workshop bridge: sends deeper goals to Baixiao Workshop with the current topic or KC context preserved.

The page includes concrete prompts, visible outputs, limitations, and a comparison with Baixiao Workshop.

## Baixiao Workshop Content

The Workshop page is organized around the complete workflow rather than a flat feature list:

1. State a goal in natural language, optionally attaching a KC or file.
2. The intent router selects one of 16 capabilities.
3. Specialist agents retrieve course evidence, create the resource, and perform grounding and safety review.
4. The result streams into the conversation and reusable artifacts are stored in the learner's study space.

The page preserves the existing capability inventory while making answer types, generated resources, evidence, uploads, visible agent activity, and AI Guide linkage easier to scan.

## Visual Direction

Keep the current warm paper-and-pixel visual language. Improve hierarchy through grouped navigation, compact orientation cards, stronger subsection rhythm, and restrained accent blocks. Pixel styling remains decorative; body content stays readable, selectable, and accessible.

## Verification

- Component tests cover grouped navigation, removal of duplicated right-side topics, responsive outline availability, and the expanded AI Guide and Workshop content.
- Type checking and the focused GuidePage test suite must pass.
- A production build and browser check verify sticky columns, topic switching, anchors, and desktop/mobile layouts.
- Public acceptance verifies `/guide`, direct topic URLs, assets, and console health after ECS deployment.
