export var ReportTransformer = {

    transform: function (xmlReport) {

        let doc = (new DOMParser).parseFromString(xmlReport, 'text/xml');
        //console.log(doc);
        let parsedReport = [];

        let testCasesIterator = doc.evaluate('//testcase', doc, null, XPathResult.ORDERED_NODE_ITERATOR_TYPE, null);
        let node;
        while (node = testCasesIterator.iterateNext()) {
            //console.log('Found node:', node); // Debugging log
            parsedReport.push(this.parseTestCase(node));
        }

        return parsedReport;
    },

    parseTestCase: function (testcaseNode) {

        let testCase = {
            type: "testcase",
            name: testcaseNode.parentNode.getAttribute("name") + "->" + testcaseNode.getAttribute("name"),
            "class": testcaseNode.getAttribute("class"),
            file: testcaseNode.getAttribute("file"),
            line: testcaseNode.getAttribute("line") * 1,
            time: testcaseNode.getAttribute("time") * 1
        };

        // Add eventual error or failure messages
        let types = ["error", "failure", "skipped"];
        for (let i = 0, c = types.length; i < c; i++) {
            let typedElements = this.getImmediateChildrenByTagName(testcaseNode, types[i]);
            if (typedElements.length) {
                let typedElement = typedElements[0];
                testCase[types[i]] = {
                    type: typedElement.getAttribute("type"),
                    message: typedElement.textContent
                }
            }
        }

        return testCase;
    },

    getImmediateChildrenByTagName: function (node, tagName) {

        let result = [];
        for (let i = 0, c = node.childNodes.length; i < c; i++) {
            let child = node.childNodes[i];
            if (child.tagName === tagName) {
                result.push(child);
            }
        }

        return result;
    },

    isXMLorHTML: function (inputString) {
        const parser = new DOMParser();
        const doc = parser.parseFromString(inputString, 'text/xml');
        const isXML = doc.getElementsByTagName('parsererror').length === 0;

        if (isXML) {
            return 'XML';
        } else {
            const doc = parser.parseFromString(inputString, 'text/html');
            const isHTML = doc.getElementsByTagName('parsererror').length === 0;
            return isHTML ? 'HTML' : 'Invalid';
        }
    }
};
