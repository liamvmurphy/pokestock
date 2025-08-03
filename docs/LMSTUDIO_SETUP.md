# LM Studio Setup Guide

## Prerequisites

1. Download and install LM Studio from https://lmstudio.ai/
2. Download the Qwen2.5-VL-7B model (or similar vision-capable model)

## Setup Steps

### 1. Install LM Studio

- Download from https://lmstudio.ai/
- Install for your platform (Windows/Mac/Linux)
- Launch LM Studio

### 2. Download Vision Model

In LM Studio:
1. Go to the "Discover" tab
2. Search for: `qwen2.5-vl-7b` or `Qwen/Qwen2.5-VL-7B-Instruct`
3. Download the model (this may take some time)

### 3. Start the Server

1. Go to "Local Server" tab in LM Studio
2. Select your downloaded vision model
3. Configure server settings:
   - Port: 1234 (default)
   - Enable CORS
   - Enable image support
4. Click "Start Server"

### 4. Verify Server is Running

Test with curl:
```bash
curl http://localhost:1234/v1/models
```

You should see your model listed.

## Testing Vision Capabilities

The application uses LM Studio for:
1. **Facebook Marketplace Analysis** - Extract Pokemon TCG items from listings
2. **Store Screenshot Analysis** - Detect stock status and prices from screenshots

### Example Request Format

```json
{
  "model": "qwen/qwen2.5-vl-7b",
  "messages": [
    {
      "role": "system",
      "content": "You are a Pokemon TCG specialist..."
    },
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Analyze this Pokemon TCG listing"
        },
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/jpeg;base64,..."
          }
        }
      ]
    }
  ],
  "temperature": 0.7,
  "max_tokens": 1000
}
```

## Troubleshooting

- **Connection refused**: Make sure LM Studio server is running
- **Model not found**: Ensure you've selected the correct model in LM Studio
- **Out of memory**: Try a smaller model or adjust context size
- **Slow responses**: Vision models are resource-intensive, be patient

## Performance Tips

1. Use GPU acceleration if available
2. Adjust context window size based on your RAM
3. Consider using quantized models for better performance
4. Keep prompts concise for faster responses