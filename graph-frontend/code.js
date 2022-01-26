"use strict";

let idField;
let showId;
let edgeListId;
let edgeListJson;
let infoJson;
let headers;
let hasInfo;

window.onload = function() 
{
    document.getElementById("edgelistsubmit").disabled = true;
    document.getElementById("infodiv").style.display = "none";
    document.getElementById("analyse").style.display = "none";
    document.getElementById("headers2").style.display = "none";
    document.getElementById("linkinput").style.display = "none";
    hasInfo = false;
};


function enableSubmit()
{
    document.getElementById("edgelistsubmit").disabled = false;
    document.getElementById("analyse").style.display = "none";
    document.getElementById("infodiv").style.display = "none";
}


function disableSubmit()
{
    document.getElementById("edgelistsubmit").disabled = true;
    document.getElementById("results").innerHTML = "";
}


function showAnalyse(i)
{
    document.getElementById("analyse").style.display = "";
    idField = i;
}


function readFile()
{
    disableSubmit();
    let edgeList = document.getElementById("edgelist").files[0];
    let infoFile = document.getElementById("info").files[0];

    if(edgeList.name.split('.').pop() === "csv")
    {
        const reader = new FileReader();
        const infoReader = new FileReader();
    
        reader.onload = function(e) 
        {
            const text = e.target.result;
            edgeListJson = csvJSON(text);
            edgeListJson = {'edgelist' : edgeListJson};
            edgeListJson['edgeListId'] = getEdgeListHeader(text);
    
            if(infoFile !== undefined)
            {
                infoReader.readAsText(infoFile);
            }
            else
            {
                showAnalyse(-1);
            }
        };
    
        infoReader.onload = function(e) 
        {
            const text = e.target.result;
            infoJson = csvJSON(text);
            edgeListJson['info'] = infoJson;
            viewInfoHeaders(text);
        };
          
        reader.readAsText(edgeList);
    }
    else if(edgeList.name.split('.').pop() === "txt")
    {
        var txtReader = new FileReader();
        txtReader.onload=function()
        {
            edgeListJson = txtJSON(txtReader.result);
            edgeListJson = {'edgelist' : edgeListJson};
            edgeListJson['edgeListId'] = txtReader.result.split(" ")[0].split("_")[0];
            edgeListId = txtReader.result.split(" ")[0].split("_")[0];;
            showAnalyse(-1);
        }
              
        txtReader.readAsText(edgeList);
    }
    
}


function viewInfoHeaders(str, delimiter = ",") 
{
    headers = str.slice(0, str.indexOf("\n")).split(delimiter);
    
    document.getElementById("infodiv").style.display = "";
    document.getElementById("headers").innerHTML = "";
    
    for(let i = 0; i < headers.length; ++i)
    {
        document.getElementById("headers").innerHTML += "<button class='secondary' onclick=\"showAnalyse(\'" + headers[i] + "\')\">" + headers[i] + "</button>\n";
    }

    hasInfo = true;
}


function getEdgeListHeader(str, delimiter = ",")
{
    headers = str.slice(0, str.indexOf("\n")).split(delimiter);
    return headers[0].split("_")[0];
}


function csvToArray(str, delimiter = ",") 
{
    const headers = str.slice(0, str.indexOf("\n")).split(delimiter);
    const rows = str.slice(str.indexOf("\n") + 1).split("\n");

    console.log(rows);

    return rows;
}


function csvJSON(csv)
{
    var lines = csv.split("\n");
    var result = [];
    var headers = lines[0].split(",");
  
    for(let i = 1; i < lines.length; i++)
    {
        var obj = {};
        var currentline = lines[i].split(",");
  
        for(var j = 0; j < headers.length; j++)
        {
            obj[headers[j]] = currentline[j];
        }
  
        result.push(obj);
    }
  
    return result;
}


function txtJSON(str)
{
    var cells = str.split('\n').map(function (el) { return el.split(/\s+/); });
    var headings = cells.shift();

    var obj = cells.map(function (el) 
    {
        var obj = {};
        for (var i = 0, l = el.length; i < l; i++) 
        {
            obj[headings[i]] = isNaN(Number(el[i])) ? el[i] : +el[i];
        }
        return obj;
    });

    return obj;
}


function selectShowId(name)
{
    showId = name;
    edgeListJson["showId"] = showId;
    sendRequest("detectfraud");
}


function detectFraud()
{
    if(hasInfo === true)
    {
        document.getElementById("headers2").style.display = "";
        document.getElementById("headers2").innerHTML = "Show by";
        for(let i = 0; i < headers.length; ++i)
        {
            console.log(headers[i]);
            document.getElementById("headers2").innerHTML += "<button class='secondary' onclick='selectShowId(\"" + headers[i] + "\")'>" + headers[i] + "</button>\n";
        }
    }
    else
    {
        showId = "id";
        edgeListJson["showId"] = "id";
        sendRequest("detectfraud");
    }
}


function showLinkInput()
{
    document.getElementById("linkinput").style.display = "";
}


function predictLink()
{
    let link1 = document.getElementById("link1").value;
    let link2 = document.getElementById("link2").value;

    edgeListJson["link1"] = link1;
    edgeListJson["link2"] = link2;

    sendRequest("predictlink");
}


function sendRequest(to)
{
    var xhr = new XMLHttpRequest();
    xhr.open("POST", "http://127.0.0.1:8080/" + to, true);
    xhr.setRequestHeader('Content-Type', 'application/json');


    edgeListJson['id'] = idField;
    edgeListJson['headers'] = headers;

    console.log("SENT:");
    console.log(edgeListJson);

    xhr.send(JSON.stringify(edgeListJson));

    document.getElementById("results").innerHTML = "<hr><div class=\"loader\"></div>";
    window.scrollTo(0,document.body.scrollHeight);

    xhr.onload = function() 
    {
        let data = JSON.parse(this.responseText);
        
        handleResponse(to, data);
    }
}


function handleResponse(to, data)
{
    if(to === "scc")
    {
        document.getElementById("results").innerHTML = "<hr><br><h4>Result</h4> <b>" + data["results"].length + " critical connections found. Some of them are:</b><br><br><table role=\"grid\"><tbody id=\"resultstable\"></tbody></table>";
        for(let i = 0; i < 10; i++)
        {
            document.getElementById("resultstable").innerHTML += "<tr><td>" + data["results"][i]["vertex1"] + " &#8594; " + data["results"][i]["vertex2"] + "</td></tr>"
        }
    }
    else if(to === "detectfraud")
    {
        document.getElementById("results").innerHTML = "<hr><br><h4>Result</h4> <b>These are the 10 most suspect nodes</b> <small><i>(Shown by " + showId + ")</i></small><br><br><table role=\"grid\"><tbody id=\"resultstable\"></tbody></table>";
        let top10 = data["results"].splice(0, 10);
        for(let i = 0; i < 10; i++)
        {
            document.getElementById("resultstable").innerHTML += "<tr><td>" + top10[i][showId] + "</td></tr>";
        }

        console.log(data);
    }
    else if(to === "density")
    {
        document.getElementById("results").innerHTML = "<hr><br><h4>Result</h4>The density of the Graph is " + data["results"].toFixed(4) * 100 + "%";
    }
    else if(to === "clustering")
    {
        document.getElementById("results").innerHTML = "<hr><br><h4>Result</h4>The clustering coefficient is " + data["results"].toFixed(4) * 100 + "%";
    }
    else if(to === "predictlink")
    {
        document.getElementById("results").innerHTML = "<hr><br><h4>Result</h4>Link prediction between " + data["link1"] + " and " + data["link2"] + " is " + data["results"].toFixed(4);
    }
    window.scrollTo(0,document.body.scrollHeight);
}