import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import zhCommon from './zh/common.json'
import zhLogin from './zh/login.json'
import zhLayout from './zh/layout.json'
import zhConnection from './zh/connection.json'
import zhTopology from './zh/topology.json'
import zhVertexType from './zh/vertexType.json'
import zhEdgeType from './zh/edgeType.json'
import zhProperty from './zh/propertyManagement.json'
import zhVertexData from './zh/vertexData.json'
import zhEdgeData from './zh/edgeData.json'
import zhGraphExplore from './zh/graphExplore.json'
import zhGremlin from './zh/gremlin.json'
import zhImportExport from './zh/importExport.json'
import zhOperationLog from './zh/operationLog.json'
import zhUserManagement from './zh/userManagement.json'
import zhUserDetail from './zh/userDetail.json'
import zhRoleManagement from './zh/roleManagement.json'
import zhRoleDetail from './zh/roleDetail.json'
import zhPermission from './zh/permissionOverview.json'
import zhLoginLog from './zh/loginLog.json'
import zhPermissions from './zh/permissions.json'

import enCommon from './en/common.json'
import enLogin from './en/login.json'
import enLayout from './en/layout.json'
import enConnection from './en/connection.json'
import enTopology from './en/topology.json'
import enVertexType from './en/vertexType.json'
import enEdgeType from './en/edgeType.json'
import enProperty from './en/propertyManagement.json'
import enVertexData from './en/vertexData.json'
import enEdgeData from './en/edgeData.json'
import enGraphExplore from './en/graphExplore.json'
import enGremlin from './en/gremlin.json'
import enImportExport from './en/importExport.json'
import enOperationLog from './en/operationLog.json'
import enUserManagement from './en/userManagement.json'
import enUserDetail from './en/userDetail.json'
import enRoleManagement from './en/roleManagement.json'
import enRoleDetail from './en/roleDetail.json'
import enPermission from './en/permissionOverview.json'
import enLoginLog from './en/loginLog.json'
import enPermissions from './en/permissions.json'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      zh: {
        common: zhCommon,
        login: zhLogin,
        layout: zhLayout,
        connection: zhConnection,
        topology: zhTopology,
        vertexType: zhVertexType,
        edgeType: zhEdgeType,
        propertyManagement: zhProperty,
        vertexData: zhVertexData,
        edgeData: zhEdgeData,
        graphExplore: zhGraphExplore,
        gremlin: zhGremlin,
        importExport: zhImportExport,
        operationLog: zhOperationLog,
        userManagement: zhUserManagement,
        userDetail: zhUserDetail,
        roleManagement: zhRoleManagement,
        roleDetail: zhRoleDetail,
        permissionOverview: zhPermission,
        loginLog: zhLoginLog,
        permissions: zhPermissions,
      },
      en: {
        common: enCommon,
        login: enLogin,
        layout: enLayout,
        connection: enConnection,
        topology: enTopology,
        vertexType: enVertexType,
        edgeType: enEdgeType,
        propertyManagement: enProperty,
        vertexData: enVertexData,
        edgeData: enEdgeData,
        graphExplore: enGraphExplore,
        gremlin: enGremlin,
        importExport: enImportExport,
        operationLog: enOperationLog,
        userManagement: enUserManagement,
        userDetail: enUserDetail,
        roleManagement: enRoleManagement,
        roleDetail: enRoleDetail,
        permissionOverview: enPermission,
        loginLog: enLoginLog,
        permissions: enPermissions,
      },
    },
    fallbackLng: 'zh',
    supportedLngs: ['zh', 'en'],
    ns: ['common'],
    defaultNS: 'common',
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'i18nextLng',
      caches: ['localStorage'],
    },
  })

export default i18n
