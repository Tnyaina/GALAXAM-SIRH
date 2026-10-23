!include "LogicLib.nsh"

!macro NSIS_HOOK_PREINSTALL
  IfFileExists "$INSTDIR\galaxam-sirh-desktop.exe" 0 skip_existing_service_before_install
  ExecWait '"$INSTDIR\galaxam-sirh-desktop.exe" --galaxam-remove-service' $0
  ${If} $0 != 0
    Abort "Impossible d'arrêter le service GALAXAM avant la mise à jour."
  ${EndIf}
  skip_existing_service_before_install:
!macroend

!macro NSIS_HOOK_POSTINSTALL
  ExecWait '"$INSTDIR\galaxam-sirh-desktop.exe" --galaxam-install-service' $0
  ${If} $0 != 0
    Abort "Impossible d'installer le service GALAXAM."
  ${EndIf}
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  IfFileExists "$INSTDIR\galaxam-sirh-desktop.exe" 0 skip_service_removal
  ExecWait '"$INSTDIR\galaxam-sirh-desktop.exe" --galaxam-remove-service' $0
  ${If} $0 != 0
    Abort "Impossible d'arrêter le service GALAXAM."
  ${EndIf}
  skip_service_removal:
!macroend
