; HisabPro Windows 10/11 64-bit NSIS Installer Script
; Produces: HisabPro_Setup.exe
; Prerequisite on Windows: run `gradle :desktopApp:createDistributable` first
; Then compile with NSIS: `makensis desktopApp\installer\HisabPro.nsi`

!include "MUI2.nsh"
!include "x64.nsh"

!define APP_NAME "HisabPro"
!define APP_VERSION "1.3.0"
!define APP_PUBLISHER "HisabPro"
!define APP_EXE "HisabPro.exe"
!define DIST_DIR "..\build\compose\binaries\main\app\HisabPro"
!define ICON_PATH "..\src\main\resources\hisabpro.ico"

Name "${APP_NAME} ${APP_VERSION}"
OutFile "HisabPro_Setup.exe"
InstallDir "$PROGRAMFILES64\${APP_NAME}"
InstallDirRegKey HKLM "Software\${APP_NAME}" "Install_Dir"
RequestExecutionLevel admin
Unicode True

!define MUI_ABORTWARNING
!define MUI_ICON "${ICON_PATH}"
!define MUI_UNICON "${ICON_PATH}"
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Launch ${APP_NAME} now"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "LICENSE.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "HisabPro Desktop Application" SecMain
    SectionIn RO
    ${If} ${RunningX64}
        SetRegView 64
    ${EndIf}

    SetOutPath "$INSTDIR"
    File /r "${DIST_DIR}\*.*"
    File "README.txt"
    File "LICENSE.txt"

    ; Store installation folder and Add/Remove Programs metadata
    WriteRegStr HKLM "Software\${APP_NAME}" "Install_Dir" "$INSTDIR"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "DisplayName" "${APP_NAME}"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "Publisher" "${APP_PUBLISHER}"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "DisplayIcon" "$INSTDIR\${APP_EXE}"
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "NoModify" 1
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "NoRepair" 1

    WriteUninstaller "$INSTDIR\uninstall.exe"

    ; Start Menu & Desktop Shortcuts
    CreateDirectory "$SMPROGRAMS\${APP_NAME}"
    CreateShortcut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0
    CreateShortcut "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe" 0
    CreateShortcut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0
SectionEnd

Section "Uninstall"
    ${If} ${RunningX64}
        SetRegView 64
    ${EndIf}

    Delete "$DESKTOP\${APP_NAME}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk"
    RMDir "$SMPROGRAMS\${APP_NAME}"

    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"
    DeleteRegKey HKLM "Software\${APP_NAME}"

    ; Note: Preserves %LOCALAPPDATA%\HisabPro SQLite offline queue and encrypted vault intentionally
    RMDir /r "$INSTDIR"
SectionEnd
