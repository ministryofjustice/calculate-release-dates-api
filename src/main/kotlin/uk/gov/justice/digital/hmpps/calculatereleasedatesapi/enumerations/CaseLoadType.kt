package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

/*
  There are three valid types COMM, INST and APP. Dummy is used in the Prison API development build.
  We have looked into making this correct in prison API, but the risk of unintended consequences are too high.
 */
enum class CaseLoadType { COMM, INST, APP, DUMMY }
