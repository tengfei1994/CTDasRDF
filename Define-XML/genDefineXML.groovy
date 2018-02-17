@Grab('org.apache.jena:jena-core:3.4.0')
@Grab('org.apache.jena:jena-arq:3.4.0')

/**
 * @author ippei
 *
 */

import groovy.xml.MarkupBuilder
import org.apache.jena.query.*

class genDefineXMLFile {
	def Sparql_Endpoint="http://localhost:8181/fuseki/CTD-RDF/sparql"
	// Init Template Engine
	def engine = new groovy.text.GStringTemplateEngine()

	// Get Study Metadata
	def ProtocolId
	def StudyDescription
	def Sponsor
	def StdVersion

	def prefixed = new File('template/prefixes.sparql.template').getText()
	def studyMetadata = new File('template/studyMetadata.sparql.template').getText()
	def queryString = prefixed + studyMetadata
	def comentCollection = []
	def methodCollection = []
	def datasetList = ["DM","SUPPDM","VS"]

	// Construct Other Metadata
	def writer = new StringWriter()
	def xml = new MarkupBuilder(writer)

	def genDefineXMLFile() {
		Query query = QueryFactory.create(queryString)
		// Execute the query and obtain results
		QueryExecution qe = QueryExecutionFactory.sparqlService(Sparql_Endpoint, query);
		try {
			for (ResultSet rs = qe.execSelect(); rs.hasNext() ; ) {
				QuerySolution sol = rs.nextSolution()
				ProtocolId=sol.StudyId
				StudyDescription=sol.StudyDescription
				Sponsor=sol.Sponsor
				StdVersion=sol.StdVersion
			}
		} finally {
			qe.close()
		}

		xml.setOmitNullAttributes(true)
		xml.setOmitEmptyAttributes(true)
		xml.setDoubleQuotes(true)
		xml.setEscapeAttributes(true)
	}


	def genDefineXML() {
		// Generate ItemGroupDef
		def _DefineXml=""
		for (i in datasetList) {
			def dsname
			if (i.size() > 4 && i[0..3]=="SUPP") {
				dsname=['Dataset':"SUPPQUAL"]
			}else{
				dsname=['Dataset':i]
			}
			def datasetMetadataTemplate = new File('template/datasetMetadata.sparql.template').getText()
			def datasetMetadataQuery = engine.createTemplate(datasetMetadataTemplate).make(dsname)
			def datasetMetadataQueryFactory = QueryFactory.create(prefixed + datasetMetadataQuery)
			QueryExecution datasetMetadataQE = QueryExecutionFactory.sparqlService(Sparql_Endpoint, datasetMetadataQueryFactory);

			// Get VariabeMetadata
			def variableMetadataTemplate = new File('template/variableMetadata.sparql.template').getText()
			def variableMetadataQuery = engine.createTemplate(variableMetadataTemplate).make(dsname)
			def variableMetadataQueryFactory = QueryFactory.create(prefixed + variableMetadataQuery)
			QueryExecution variableMetadataQE = QueryExecutionFactory.sparqlService(Sparql_Endpoint, variableMetadataQueryFactory);

			// Get VariabeMetadata for ItemDef
			def variableItemDefMetadataTemplate = new File('template/variableMetadata.sparql.template').getText()
			def variableItemDefMetadataQuery = engine.createTemplate(variableItemDefMetadataTemplate).make(dsname)
			def variableItemDefMetadataQueryFactory = QueryFactory.create(prefixed + variableItemDefMetadataQuery)
			QueryExecution variableItemDefMetadataQE = QueryExecutionFactory.sparqlService(Sparql_Endpoint, variableItemDefMetadataQueryFactory);

			_DefineXml <<= genItemGroupDef(datasetMetadataQE, variableMetadataQE, i)
		}

		// Get VariabeMetadata for ItemDef
		for (i in datasetList) {
			def dsname
			if (i.size() > 4 && i[0..3]=="SUPP") {
				dsname=['Dataset':"SUPPQUAL"]
			}else{
				dsname=['Dataset':i]
			}
			def variableItemDefMetadataTemplate = new File('template/variableMetadata.sparql.template').getText()
			def variableItemDefMetadataQuery = engine.createTemplate(variableItemDefMetadataTemplate).make(dsname)
			def variableItemDefMetadataQueryFactory = QueryFactory.create(prefixed + variableItemDefMetadataQuery)
			QueryExecution variableItemDefMetadataQE = QueryExecutionFactory.sparqlService(Sparql_Endpoint, variableItemDefMetadataQueryFactory);

			_DefineXml <<= genItemDef(variableItemDefMetadataQE,i)
		}
			// Generate def:comment
			_DefineXml <<= genComment()
			// Construct CreationDataTime
			def CreationDateTime = new Date(System.currentTimeMillis()).format("yyyy-MM-dd'T'HH:mm:ss")
			def f = new File('template/define.xml.template')
			def binding = ['ProtocolId': ProtocolId,
						   'StudyDescription': StudyDescription,
						   'Sponsor': Sponsor,
						   'StdVersion': StdVersion,
						   'CreationDateTime': CreationDateTime,
						   'OtherMetadata': _DefineXml]
			def template = engine.createTemplate(f).make(binding)
			def fileWriter = new File('define.xml')
			fileWriter.write template.toString()
	}

	def genItemGroupDef(QueryExecution datasetMetadataQE, QueryExecution variableMetadataQE, String datasetName) {
      	def writer = new StringWriter()
  		def xml = new MarkupBuilder(writer)

  		xml.setOmitNullAttributes(true)
  		xml.setOmitEmptyAttributes(true)
  		xml.setDoubleQuotes(true)
  		xml.setEscapeAttributes(true)
        for (ResultSet datasetResultset = datasetMetadataQE.execSelect(); datasetResultset.hasNext() ; ) {
            QuerySolution sol = datasetResultset.nextSolution()
            def dsname
            def dslabel
            def repeating
            def reference
            if (sol.Domain.toString() == "SUPPQUAL") {
                dsname = datasetName
                dslabel = sol.DatasetLabel.toString() + " for " + datasetName.toString()
            }else{
                dsname = sol.Domain
                dslabel = sol.DatasetLabel
            }
            // Repeating Attribute
            if (dsname in ["DM", "TA", "TI", "TE", "TV", "TS"]) {
                repeating = "No"
            }else{
                repeating = "Yes"
            }
            // IsReferenceData Attribute
            if (sol.defClass == "TRIAL DESIGN") {
                reference = "Yes"
            }else{
                reference = "No"
            }
  		    xml.'ItemGroupDef'(
				'OID': "IG.${dsname}",
				'Name': dsname,
				'Domain': dsname,
				'SASDatasetName': dsname,
				'Repeating': repeating,
				'IsReferenceData': reference,
				'Purpose': "Tabulation",
				'def:Structure': sol.Structure,
				'def:Class': sol.defClass,
				'def:CommentOID': "",
				'def:ArchiveLocationID': "LF.${dsname}"
  			) {
  				'Description'({'TranslatedText'('xml:lang':"en",  dslabel )})
                    for (ResultSet variableResultset = variableMetadataQE.execSelect(); variableResultset.hasNext() ; ) {
                        QuerySolution sol2 = variableResultset.nextSolution()
  						'ItemRef'(ItemOID: "IT.${datasetName}.${sol2.dataElementName}", OrderNumber: sol2.ordinal_, Mandatory: sol2.Core, KeySequence: "", MethodOID: "")
  					}
  					'def:leaf'('ID':"LF.${dsname}", 'xlink:href':"${dsname}.xpt".toLowerCase(), {'def:title'("${dsname}.xpt".toLowerCase())}
  					)
  				}
  		return(writer)
  	}
  }


	def String genItemDef(QueryExecution variableItemDefMetadataQE, String datasetName) {
      	def writer = new StringWriter()
      	def xml = new MarkupBuilder(writer)
      	xml.setOmitNullAttributes(true)
		xml.setOmitEmptyAttributes(true)
    		xml.setDoubleQuotes(true)
    		xml.setEscapeAttributes(true)

   		for (ResultSet variableItemDefResultset = variableItemDefMetadataQE.execSelect(); variableItemDefResultset.hasNext(); ) {
            QuerySolution solVarItem = variableItemDefResultset.nextSolution()
            def comOID
            if (solVarItem.comment != null){
                Map comMap =[("COM."+solVarItem.dataElementName):(solVarItem.comment)]
                comentCollection << comMap
                comOID = "COM."+solVarItem.dataElementName
            }
           xml.'ItemDef'(
			    'OID': "IT.${datasetName}.${solVarItem.dataElementName}",
			    'Name': solVarItem.dataElementName,
			    'SASFieldName': solVarItem.dataElementName,
			    'DataType': "",
			    'SignificantDigits': "",
			    'Length': "",
			    'def:DisplayFormat': "",
          		"def:CommentOID": comOID
			    ) {
				         'Description'({'TranslatedText'('xml:lang':"en",  solVarItem.dataElementLabel )})
                 if (solVarItem.Origin != null){
				                  'def:Origin'(Type:solVarItem.Origin, {'Description'({'TranslatedText'('xml:lang':"en",  solVarItem.Origin )})})
                 }
				           if (solVarItem.codeList != ""){
					         'CodeListRef'('CodeListOID': solVarItem.codeList )
				           }
			      }
        }
  		return(writer)
    }




    def String genComment( ){
		println("Call genComment Method")
		def writer = new StringWriter()
		def xml = new MarkupBuilder(writer)

		xml.setOmitNullAttributes(true)
		xml.setOmitEmptyAttributes(true)
		xml.setDoubleQuotes(true)
		xml.setEscapeAttributes(true)

		for (comind in comentCollection){
			def ckey = comind.keySet()[0]
			def cval = comind.get(ckey)
			println( ckey + ":" + cval)
        		xml.'def:CommentDef'( 'OID': ckey ){
          		'Description'({ 'TranslatedText'('xml:lang':"en",  comind.get( ckey )) })
        		}
      	}
	  return(writer)
 	}

    def setCommet(String comOID, String comDescription){

    }
}



define_generator = new genDefineXMLFile()
define_generator.genDefineXML()
