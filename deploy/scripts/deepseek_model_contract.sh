#!/usr/bin/env bash

# Keep these IDs aligned with DEEPSEEK_CHAT_MODELS and DEEPSEEK_VISION_MODELS
# in services/pythontopia-graph/pythontopia_graph/llm/provider.py.
deepseek_model_is_supported() {
  case "$1:$2" in
    chat:deepseek-flash|chat:deepseek-v4-pro|fast:deepseek-flash|fast:deepseek-v4-pro|vision:deepseek-flash) return 0 ;;
    *) return 1 ;;
  esac
}
