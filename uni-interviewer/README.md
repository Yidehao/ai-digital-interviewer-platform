# AI Interviewer - Uni-App Project

## Project Overview

This is a cross-platform AI Interviewer application built with uni-app framework. The application provides an interactive interview experience where candidates can answer questions through video-based AI interviews with voice recording capabilities.

## Technology Stack

- **Framework**: uni-app (Vue 2)
- **Platform Support**: App (Android/iOS), H5, Mini Programs
- **Backend Communication**: RESTful API
- **Audio Processing**: uni.getRecorderManager() for voice recording
- **Video Playback**: Native video component with AI interviewer videos

## Project Structure

```
uni-interviewer/
├── App.vue                 # Main application entry, global configuration
├── main.js                  # Application initialization, utility imports
├── pages.json               # Page routing configuration
├── manifest.json            # App manifest, platform-specific settings
├── index.html              # H5 entry HTML
│
├── pages/                   # Page components
│   ├── welcome.vue         # Login/verification page
│   ├── welcome.css         # Styles for welcome page
│   ├── interviewer.vue     # Main AI interview page
│   ├── interviewer.css     # Styles for interviewer page
│   ├── me.vue              # Interview completion/submission page
│   └── me.css              # Styles for completion page
│
├── components/             # Reusable components and utilities
│   ├── Common.js           # Common utilities (sound playback)
│   ├── DateUtil.js         # Date/time formatting utilities
│   ├── NameUtil.js         # Name processing utilities (Pinyin conversion)
│   └── videoDetail.vue     # Video detail component
│
├── json/                    # Data files
│   ├── area_province.js     # US States data
│   ├── area_city.js         # US Cities data
│   └── area_district.js     # US Counties data
│
├── static/                  # Static resources
│   ├── ai/                  # AI interviewer video files
│   ├── faces/               # Face images
│   ├── files/               # General files (backgrounds, sounds)
│   ├── fonts/               # Icon fonts
│   ├── icons/               # Icon images
│   ├── images/              # Image resources
│   ├── resources/           # Additional resources (renamed from itzixi)
│   └── videos/              # Video files
│
└── uni_modules/             # uni-app modules
    ├── uni-badge/          # Badge component
    ├── uni-popup/          # Popup component
    ├── uni-scss/           # SCSS utilities
    └── uni-transition/     # Transition component
```

## Application Flow

### 1. Welcome Page (`pages/welcome.vue`)
- **Purpose**: User authentication and verification
- **Features**:
  - Phone number input with +1 prefix (US)
  - SMS verification code
  - 60-second countdown timer for code resend
  - Backend API integration for verification
- **API Endpoints**:
  - `POST /welcome/getSMSCode` - Request verification code
  - `POST /welcome/verify` - Verify code and login

### 2. Interviewer Page (`pages/interviewer.vue`)
- **Purpose**: Main interview interface
- **Features**:
  - AI interviewer video playback
  - Question display overlay on video
  - Voice recording functionality
  - Question navigation (next question)
  - Real-time timer display
  - Answer submission
- **Workflow**:
  1. Load questions from backend (`GET /questionLib/prepareQuestion`)
  2. Display AI interviewer video with question overlay
  3. User clicks "Click to Answer" to start recording
  4. User speaks answer (recorded audio)
  5. User clicks "Answer Complete, Next Question"
  6. Audio uploaded to server (`POST /speech/uploadVoice`)
  7. Repeat for all questions
  8. Submit interview (`POST /interviewRecord/collect`)

### 3. Completion Page (`pages/me.vue`)
- **Purpose**: Display interview completion status
- **Features**:
  - Success message
  - Project information
  - Copyright notice

## Key Components

### App.vue (Global Configuration)
- **Global Data**:
  - `serverUrl`: Backend server URL
  - `env`: Environment configuration (production/development)
  - `provinceList`, `cityList`, `districtList`: US States, Cities, and Counties data
- **Global Methods**:
  - `getAge()`: Calculate age from birthday
  - `isStrEmpty()`: Check if string is empty
  - `userIsLogin()`: Check user login status
  - `getUserSessionToken()` / `setUserSessionToken()`: Token management
  - `getUserInfoSession()` / `setUserInfoSession()`: User info management
  - `getDateBeforeNow()`: Format relative time
  - `dateFormat()`: Format date string

### Utility Components

#### DateUtil.js
- Date/time formatting utilities
- Relative time display (e.g., "5 minutes ago", "2 hours ago")
- Date comparison functions
- Local date/time string conversion

#### NameUtil.js
- Name processing utilities
- Number validation
- String processing utilities

#### Common.js
- Sound playback functions
- `playSendSound()`: Play sound for sending messages
- `playReceiveSound()`: Play sound for receiving messages

## Configuration

### Backend Server
Configure the backend server URL in `App.vue`:
```javascript
globalData: {
    serverUrl: "http://10.0.0.228:8080",  // Update with your server URL
    env: "production",  // or "development"
}
```

### API Endpoints
The application expects the following backend endpoints:

1. **Authentication**:
   - `POST /welcome/getSMSCode?mobile={phone}` - Get SMS verification code
   - `POST /welcome/verify` - Verify code and login

2. **Interview**:
   - `GET /questionLib/prepareQuestion?candidateId={id}` - Get interview questions
   - `POST /speech/uploadVoice` - Upload recorded audio
   - `POST /interviewRecord/collect` - Submit interview results

### Manifest Configuration
- Update splash screen paths in `manifest.json` (currently placeholder paths)
- Configure app permissions for camera, microphone, storage
- Set up platform-specific settings (Android/iOS)

## Development Setup

### Prerequisites
- HBuilderX or compatible IDE
- Node.js (for H5 development)
- Android Studio / Xcode (for native app development)

### Running the Project

1. **H5 Development**:
   ```bash
   # Run in HBuilderX or use CLI
   npm run dev:h5
   ```

2. **App Development**:
   - Open project in HBuilderX
   - Select target platform (Android/iOS)
   - Run on device or emulator

3. **Mini Program**:
   - Configure in `manifest.json`
   - Run in respective mini program IDE

## Build and Deployment

### App Build
1. Configure `manifest.json` with app information
2. Set up signing certificates (Android/iOS)
3. Configure splash screens and app icons
4. Build in HBuilderX: **发行** → **原生App-云打包**

### H5 Build
1. Configure `manifest.json` H5 settings
2. Build: **发行** → **网站-H5**

## Key Features

### Voice Recording
- Uses `uni.getRecorderManager()` for audio recording
- Supports up to 10 minutes per recording
- Sample rate: 16000 Hz
- Auto-upload to server after recording stops
- Speech-to-text conversion on backend

### Video Playback
- AI interviewer videos in `static/ai/` directory
- Video overlay with question text
- Auto-play functionality
- Disabled user controls for interview flow

### State Management
- Session storage for user tokens and info
- Global state in `App.vue` globalData
- Question and answer state in component data

## File Naming Conventions

- **Pages**: `pages/{pageName}.vue` with corresponding `{pageName}.css`
- **Components**: `components/{componentName}.vue` or `{componentName}.js`
- **Static Resources**: Organized by type in `static/` subdirectories
- **Utilities**: `{UtilityName}Util.js` for utility functions

## Important Notes

1. **Backend Dependency**: This is a frontend-only project. A backend server is required for full functionality.

2. **API Response Format**: Backend should return:
   ```json
   {
     "status": 200,
     "msg": "Success message",
     "data": { ... }
   }
   ```

3. **Audio Format**: Recorded audio is in MP3 format (App) or AAC format (Mini Program).

4. **Video Format**: AI interviewer videos should be in MP4 format, optimized for mobile playback.

5. **Environment Variables**: Update `serverUrl` and `env` in `App.vue` for different environments.

## Troubleshooting

### Common Issues

1. **Audio Recording Not Working**:
   - Check microphone permissions in `manifest.json`
   - Verify device permissions are granted
   - Check `recorderManager` initialization

2. **Video Not Playing**:
   - Verify video file paths
   - Check video format compatibility
   - Ensure video files exist in `static/ai/`

3. **API Connection Issues**:
   - Verify `serverUrl` in `App.vue`
   - Check network connectivity
   - Verify CORS settings on backend

4. **Build Errors**:
   - Check `manifest.json` configuration
   - Verify all required files exist
   - Check platform-specific settings

## Learning Resources

### For Students

1. **Uni-App Basics**:
   - Learn Vue.js fundamentals
   - Understand uni-app page lifecycle
   - Study component communication

2. **Key Concepts**:
   - **Pages**: Each page is a Vue component
   - **Global Methods**: Defined in `App.vue`, accessible via `getApp()`
   - **Conditional Compilation**: Use `#ifdef` for platform-specific code
   - **API Calls**: Use `uni.request()` for HTTP requests
   - **Storage**: Use `uni.setStorageSync()` / `uni.getStorageSync()`

3. **Recommended Learning Path**:
   - Start with `pages/welcome.vue` to understand form handling
   - Study `pages/interviewer.vue` for complex state management
   - Review utility components for reusable code patterns
   - Practice with API integration

## License

This is a demo project for educational purposes. Source code and related content copyright reserved.

## Contact

For questions or issues, please refer to the project documentation or contact the development team.

---

**Note**: This project has been sanitized for educational use. All sensitive information (personal names, company names, specific URLs) has been replaced with generic placeholders. Update configuration files with your own values before deployment.
