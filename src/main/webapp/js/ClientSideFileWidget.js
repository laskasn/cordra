function ClientSideFileWidget(container, onFileReadCallback, isReadAsTextConstructorParam, buttonText, isButtonSmall) {
    var self = this;
    var fileInput = null;
    var isReadAsText = false;
    
    function constructor() {
        fileInput = $('<input type="file"/>');
        container.append(fileInput);
        prettifyThisFileInput(fileInput);
        setIsReadAsText(isReadAsTextConstructorParam);
    }
    
    function readFileAsBase64() {
        if (this.files.length === 0) return;
        var reader = new FileReader();
        reader.onload = onload;
        reader.readAsDataURL(this.files[0]);
    }

    function readFileAsBytes() {
        if (this.files.length === 0) return;
        var reader = new FileReader();
        reader.onload = onload;
        reader.readAsArrayBuffer(this.files[0]); 
    }

    function readFileAsText() {
        if (this.files.length === 0) return;
        var reader = new FileReader();
        reader.onload = onload;
        reader.readAsText(this.files[0]);
    }
    
    function onload(event) {
        var data = "";
        if (isReadAsText) {
            data = event.target.result;
        } else {
            data = new Uint8Array(event.target.result);
        }
        onFileReadCallback(data);
    }
    
    function clear() {
        fileInput[0].value = ''; 
        onFileReadCallback(null);
        fileInput.trigger('change');
    }
    
    function setIsReadAsText(isReadAsTextParam) {
        isReadAsText = isReadAsTextParam;
        if (isReadAsText) {
            fileInput.on('change', readFileAsText);
        } else {
            fileInput.on('change', readFileAsBytes);
        }
    }
    
    function prettifyThisFileInput(fileInput) {
        fileInput = $(fileInput);
        if(fileInput.css('left')==='-1999px') return;
        fileInput.css('position','absolute');
        fileInput.css('left','-1999px');
        var textForButton = "Choose files";
        if (buttonText !== undefined) {
            textForButton = buttonText;
        }
        var button = $('<button class="btn btn-sm btn-default" style="min-width: 130px;" type="button"></button>');
        
        button.text(textForButton);
        if (isButtonSmall) {
            button.addClass("btn-sm");
        } 
        var span = $('<span class="help-inline">No files chosen</span>');
        fileInput.before(button, " ", span);
        button.off('click').on('click', function(event) {
            event.stopImmediatePropagation();
            fileInput.trigger('click');
        });
        fileInput.on('change', function() {
            if(fileInput[0].files.length===0) {
                span.text('No files chosen');
            } else if(fileInput[0].files.length===1) {
                span.text(fileInput[0].files[0].name);
            } else {
                span.text(fileInput[0].files.length + ' files');
            }
        });
    }    
    
    self.setIsReadAsText = setIsReadAsText;
    self.clear = clear;
    constructor();
}
